#!/bin/bash

sigint_handler()
{
  kill $PID
  exit
}

trap sigint_handler SIGINT

# Create a virtual environment if it doesn't exist
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
fi

# Activate the virtual environment
source venv/bin/activate

# Install Python dependencies
echo "Installing Python dependencies..."
pip install -r requirements.txt

# Start Python daemon in the background restart it if the script changes (using inotifywait)
echo "Starting Python daemon..."

while true; do
    python3 click_daemon.py &
    PID=$!
    inotifywait -e modify click_daemon.py
    kill $PID
done
