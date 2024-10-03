import socket
import threading
import time
import pyautogui


# Function to run keyboard commands
def walk_jump():
    with pyautogui.hold('w'):
        time.sleep(0.4)
        with pyautogui.hold('space'):
            time.sleep(0.4)

def jump_place():
    with pyautogui.hold('space'):
        time.sleep(0.4)
        pyautogui.press('u')

# Function to place a block
def place_block():
    pyautogui.press('u')

def sneak_back_place():
    with pyautogui.hold('shift'):
        time.sleep(0.4)
        with pyautogui.hold('s'):
            # Repeat 10 times
            for i in range(10):
                time.sleep(0.4)
                pyautogui.press('u')
        time.sleep(0.4)
        
def sneak_walk():
    with pyautogui.hold('shift'):
        time.sleep(0.4)
        with pyautogui.hold('w'):
            time.sleep(2)
        time.sleep(0.4)

# Make dictionary of commands and functions
commands = {
    'WALK_JUMP': walk_jump,
    'JUMP_PLACE': jump_place,
    'PLACE': place_block,
    'SNEAK_PLACE': sneak_back_place,
    'SNEAK_WALK': sneak_walk
}

# Function to handle client connections
def handle_client(connection, address):
    print(f'Connected to {address}')
    while True:
        try:
            # Receive the message from the client
            message = connection.recv(1024).decode('ascii').strip()
            if not message:
                print(f'Connection closed by {address}')
                break
            print(f'Received message: {message}.')
            
            # Process the message and run commands if necessary
            if message in commands.keys():
                commands[message]()
                response = f'{message} command executed successfully.\n'
            else:
                response = f'Unknown command {message}.\n'

            # Send a response back to the client
            connection.sendall(response.encode('utf-8'))

        except Exception as e:
            print(f'Error: {e}')
            break

    connection.close()
    print(f'Connection with {address} closed.')

def main():
    host = '127.0.0.1'  # Localhost
    port = 12345  # Port to listen on

    # Create a socket and allow address reuse
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)  # Allow socket reuse

    server_socket.bind((host, port))
    server_socket.listen(5)
    print('Waiting for a connection...')

    while True:
        try:
            # Set a timeout for accepting connections (e.g., 10 seconds)
            server_socket.settimeout(60)
            
            # Accept a new connection
            conn, addr = server_socket.accept()
            threading.Thread(target=handle_client, args=(conn, addr)).start()
        
        except socket.timeout:
            print('Waiting for connection timed out, retrying...')
        except Exception as e:
            print(f'Error while accepting connection: {e}')

if __name__ == "__main__":
    main()