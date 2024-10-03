package com.fivethreeo;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import net.minecraft.util.Hand;
import net.minecraft.client.gui.screen.Screen;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.Scanner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UnlockCursorMod implements ClientModInitializer {


    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Thread responseListenerThread;
    private ExecutorService reconnectExecutor;
    private boolean running = true;  // Control the running state of the client

    private KeyBinding unlockCursorKey;
    private KeyBinding disableModKey;
    private KeyBinding placeItemKey;
    private KeyBinding sneakPlaceItemKey;
    private KeyBinding sneakWalkKey;
    private KeyBinding walkJumpKey;
    private KeyBinding jumpPlaceKey;

    private boolean cursorUnlocked = false;
    private boolean needsReset = false;
    private boolean modEnabled = true; // New boolean to track if the mod is enabled
    private float savedYaw;
    private float savedPitch;
    private GLFWCursorPosCallback originalMouseCallback;
    private static Screen previousScreen = null;

    @Override
    public void onInitializeClient() {
        
        reconnectExecutor = Executors.newSingleThreadExecutor();  // Executor for reconnection

        connectToServerAsync();  // Connect to the server asynchronously

        // Keybinding to unlock the cursor (e.g., "U" key)
        unlockCursorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unlockcursor.unlock", // Translation key for the name of the keybinding
                InputUtil.Type.KEYSYM, // The type of keybinding (keyboard)
                GLFW.GLFW_KEY_U, // Default key (U key)
                "category.unlockcursor.general" // Translation key for the category of keybindings
        ));

        // Keybinding to disable/enable the mod (e.g., "P" key)
        disableModKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unlockcursor.disablemod", // Translation key for the name of the keybinding
                InputUtil.Type.KEYSYM, // The type of keybinding (keyboard)
                GLFW.GLFW_KEY_P, // Default key (P key)
                "category.unlockcursor.general" // Translation key for the category of keybindings
        ));

        // Keybinding to place an item (simulate right-click, e.g., "O" key)
        placeItemKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unlockcursor.placeitem", // Translation key for the name of the keybinding
                InputUtil.Type.KEYSYM, // The type of keybinding (keyboard)
                GLFW.GLFW_KEY_O, // Default key (O key)
                "category.unlockcursor.general" // Translation key for the category of keybindings
        ));

        // Keybinding to sneak and place an item (simulate right-click, e.g., "L" key)
        sneakPlaceItemKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unlockcursor.sneakplaceitem", // Translation key for the name of the keybinding
                InputUtil.Type.KEYSYM, // The type of keybinding (keyboard)
                GLFW.GLFW_KEY_L, // Default key (L key)
                "category.unlockcursor.general" // Translation key for the category of keybindings
        ));

        // Keybinding to sneak and walk (e.g., "K" key)
        sneakWalkKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unlockcursor.sneakwalk", // Translation key for the name of the keybinding
                InputUtil.Type.KEYSYM, // The type of keybinding (keyboard)
                GLFW.GLFW_KEY_K, // Default key (K key)
                "category.unlockcursor.general" // Translation key for the category of keybindings
        ));

        // Keybinding to walk and jump (e.g., "J" key)
        walkJumpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unlockcursor.walkjump", // Translation key for the name of the keybinding
                InputUtil.Type.KEYSYM, // The type of keybinding (keyboard)
                GLFW.GLFW_KEY_J, // Default key (J key)
                "category.unlockcursor.general" // Translation key for the category of keybindings
        ));

        // Keybinding to jump and place an item (e.g., "I" key)
        jumpPlaceKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unlockcursor.jumpplace", // Translation key for the name of the keybinding
                InputUtil.Type.KEYSYM, // The type of keybinding (keyboard)
                GLFW.GLFW_KEY_I, // Default key (I key)
                "category.unlockcursor.general" // Translation key for the category of keybindings
        ));

        // Register tick events for the mod
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                if (modEnabled) {
                    // Handle unlock cursor key
                    if (unlockCursorKey.wasPressed()) {
                        toggleCursor(client);
                    }

                    // Handle placing an item (simulating right-click)
                    if (placeItemKey.wasPressed()) {
                       sendToPython("PLACE");
                    }

                    // Handle sneaking and placing an item (simulating right-click)
                    if (sneakPlaceItemKey.wasPressed()) {
                        sendToPython("SNEAK_PLACE");
                    }

                    // Handle sneaking and walking
                    if (sneakWalkKey.wasPressed()) {
                        sendToPython("SNEAK_WALK");
                    }

                    // Handle walking and jumping
                    if (walkJumpKey.wasPressed()) {
                        sendToPython("WALK_JUMP");
                    }

                    // Handle jumping and placing an item
                    if (jumpPlaceKey.wasPressed()) {
                        sendToPython("JUMP_PLACE");
                    }

                }
                // Handle disabling/enabling the mod
                if (disableModKey.wasPressed()) {
                    toggleMod();
                }
            }
            if (client.player != null && cursorUnlocked && needsReset) {
                resetLookDirection(client);
            }
        });


        // Hook into screen opening events (for handling inventory opening)
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            handleScreenOpen(client, screen);
        });
    }
    
    private void sendToPython(String message) {
        // Check if the socket and output stream are initialized and the socket is connected
        if (socket == null || socket.isClosed() || outputStream == null) {
            System.err.println("Socket is closed or output stream is not initialized. Cannot send message: " + message);
            
            // Attempt to reconnect
            connectToServerAsync();
            return;  // Exit this method, as we cannot send a message without a valid connection
        }

        try {
            outputStream.write((message + "\n").getBytes());  // Send the message with a newline
            outputStream.flush();  // Flush the stream to ensure the message is sent
            System.out.println("Message sent to Python: " + message);
        } catch (IOException e) {
            System.err.println("Error while sending message to Python: " + e.getMessage());
            e.printStackTrace();
            
            // Handle reconnection logic if needed
            closeConnections();  // Close current connections
            connectToServerAsync();  // Attempt to reconnect asynchronously
        }
    }

    
    // Attempt to connect to the server asynchronously (non-blocking)
    private void connectToServerAsync() {
        reconnectExecutor.execute(() -> {
            while (running) {
                try {
                    // Attempt to connect
                    socket = new Socket();
                    socket.setReuseAddress(true);  // Allow socket reuse
                    socket.connect(new InetSocketAddress("127.0.0.1", 12345), 10000); // 10-second timeout

                    outputStream = socket.getOutputStream();
                    inputStream = socket.getInputStream();
                    System.out.println("Connected to Python daemon.");

                    // Start a separate thread to listen for responses from the daemon
                    if (responseListenerThread == null || !responseListenerThread.isAlive()) {
                        responseListenerThread = new Thread(this::listenForResponses);
                        responseListenerThread.start();
                    }

                    // If connected successfully, break out of the retry loop
                    break;

                } catch (IOException e) {
                    System.err.println("Failed to connect or lost connection, retrying in 5 seconds...");
                    closeConnections();  // Ensure any existing connections are closed before retrying
                    
                    // Sleep for a while before retrying
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();  // Ensure the thread is correctly interrupted
                        return;  // Exit if the thread was interrupted
                    }
                }
            }
        });
    }

    // Separate thread to listen for responses from the Python daemon
    private void listenForResponses() {
        try (Scanner scanner = new Scanner(inputStream)) {
            while (running && scanner.hasNextLine()) {
                String response = scanner.nextLine();
                System.out.println("Received response from daemon: " + response);
            }
        } catch (Exception e) {
            System.err.println("Connection lost while listening for responses. Attempting to reconnect...");
            // Trigger reconnection logic in case the connection is lost
            connectToServerAsync();
        } finally {
            // Ensure connections are closed properly
            closeConnections();
        }
    }

    private void closeConnections() {
        try {
            if (outputStream != null) outputStream.close();
            if (inputStream != null) inputStream.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to toggle the cursor lock state (invoked in END_CLIENT_TICK)
    private void toggleCursor(MinecraftClient client) {
        if (client != null && client.mouse != null) {
            long windowHandle = client.getWindow().getHandle();

            if (cursorUnlocked) {
                // Lock the cursor back to the game
                cursorUnlocked = false;
                System.out.println("Cursor locked!");
                sendToPython("lock");
                // Restore original callback
                GLFW.glfwSetCursorPosCallback(windowHandle, originalMouseCallback);

                // Set GLFW input mode back to disabled (locked cursor)
                GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            } else {
                // Unlock the cursor and prevent mouse look updates
                cursorUnlocked = true;
                System.out.println("Cursor unlocked!");
                sendToPython("unlock");
                // Save the current look direction (yaw and pitch)
                savedYaw = client.player.getYaw();
                savedPitch = client.player.getPitch();

                // Override the cursor position callback
                if (originalMouseCallback != null) {
                    GLFW.glfwSetCursorPosCallback(windowHandle, null);
                } else {
                    originalMouseCallback = GLFW.glfwSetCursorPosCallback(
                        windowHandle, 
                        null
                    );
                }
                
                // Set GLFW to normal cursor mode
                GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            }
        }
    }

    // Method to reset the player's yaw and pitch (invoked in START_CLIENT_TICK)
    private void resetLookDirection(MinecraftClient client) {
        if (client.player != null) {
            // Reset yaw and pitch to the saved values at the start of each tick
            client.player.setYaw(savedYaw);
            client.player.setPitch(savedPitch);
            needsReset = false; // Reset the needsReset flag after resetting
        }
    }


    // Toggle enabling/disabling the mod
    private void toggleMod() {
        modEnabled = !modEnabled;
        needsReset = true; // Reset the needsReset flag when toggling the mod
        System.out.println("Mod " + (modEnabled ? "enabled" : "disabled"));
    }

    private void handleScreenOpen(MinecraftClient client, Screen screen) {
        if (client != null && client.mouse != null) {
            long windowHandle = client.getWindow().getHandle();

            if (screen != null && cursorUnlocked) {
                // Restore mouse behavior if inventory (or any screen) is opened
                System.out.println("Inventory or screen opened, restoring cursor");
                GLFW.glfwSetCursorPosCallback(windowHandle, originalMouseCallback);
                GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            } else if (screen == null && modEnabled && cursorUnlocked) {
                // When the screen is closed and cursor unlocked, re-apply our custom behavior
                System.out.println("Screen closed, re-locking cursor if necessary");
                GLFW.glfwSetCursorPosCallback(windowHandle, null);
                GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            }
        }
    }
}
