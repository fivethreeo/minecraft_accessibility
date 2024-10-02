# Minecraft Accessibility
I have a disability that requires me to use a onscreen keyboard.
No current solutions could be found, so me and chatgpt and copilot rolled our own
## The solution
A minecraft mod that unlocks the mouse while keeping focus on the minecraft window and a python daemon to do complex key combinations
## How to use

```shell
docker compose up
# install fabric and fabric api and the built module in minecraft
# run the daemo, requires python
./start_click_daemon.sh
# Start minecraft and check keybindings
# Start onscreen keyboard, keys work when unlock key is tapped, i set unlock key to right mouse button
```

