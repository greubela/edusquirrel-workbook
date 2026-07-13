## todo: actually implement script (: 

#!/usr/bin/env nix-shell
#!nix-shell -i python3 -p "python3.withPackages (ps: [ ps.paramiko ])"

import os
import sys
import paramiko

# --- Configuration ---
SERVER = "://example.com"
PORT = 22
USER = "nixos"
REMOTE_DIR = "/home/nixos/backups"
LOCAL_FILE = "data.txt"
REMOTE_FILE = "backup_data.txt"

# New Configuration for Directory Transfer
LOCAL_SRC_DIR = "./my_local_folder"       # Local directory to copy
REMOTE_DEST_DIR = "my_remote_folder"     # Target folder name under REMOTE_DIR

SSH_KEY_PATH = os.path.expanduser("~/.ssh/id_rsa")

def sftp_upload_dir(sftp, local_dir, remote_dir):
    """Recursively uploads a local directory to a remote SFTP path."""
    print(f"Syncing directory: {local_dir} -> {remote_dir}")
    
    # Create the top-level remote directory if it doesn't exist
    try:
        sftp.mkdir(remote_dir)
    except IOError:
        pass  # Directory likely already exists

    for item in os.listdir(local_dir):
        local_path = os.path.join(local_dir, item)
        # Always use forward slashes for the remote Linux target paths
        remote_path = f"{remote_dir}/{item}"

        if os.path.isdir(local_path):
            # Recursively dive into subdirectories
            sftp_upload_dir(sftp, local_path, remote_path)
        else:
            # Upload individual files
            print(f"  Uploading file: {item}")
            sftp.put(local_path, remote_path)

def main():
    print("=== Starting Platform-Independent Network Script ===")

    # Validate local paths
    if not os.path.exists(SSH_KEY_PATH):
        print(f"Error: SSH private key not found at {SSH_KEY_PATH}")
        sys.exit(1)
        
    if not os.path.exists(LOCAL_SRC_DIR):
        print(f"Error: Local directory not found: {LOCAL_SRC_DIR}")
        sys.exit(1)

    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())

    try:
        print(f"Loading private key from {SSH_KEY_PATH}...")
        private_key = paramiko.RSAKey.from_private_key_file(SSH_KEY_PATH)

        print(f"Connecting to {SERVER}...")
        ssh.connect(hostname=SERVER, port=PORT, username=USER, pkey=private_key)

        # 1. Ensure the base remote backup directory exists
        print("Checking base remote directory...")
        ssh.exec_command(f"mkdir -p {REMOTE_DIR}")

        # 2. Open SFTP Session
        sftp = ssh.open_sftp()
        
        # 3. Upload the single file
        if os.path.exists(LOCAL_FILE):
            remote_full_file_path = f"{REMOTE_DIR}/{REMOTE_FILE}"
            print(f"Uploading single file to {remote_full_file_path}...")
            sftp.put(LOCAL_FILE, remote_full_file_path)

        # 4. Upload the directory recursively
        target_remote_dir = f"{REMOTE_DIR}/{REMOTE_DEST_DIR}"
        sftp_upload_dir(sftp, LOCAL_SRC_DIR, target_remote_dir)
        
        # Clean up
        sftp.close()
        ssh.close()
        print("=== Process Complete ===")

    except Exception as e:
        print(f"An error occurred: {e}")
        sys.exit(1)

if name == "main":
    main()