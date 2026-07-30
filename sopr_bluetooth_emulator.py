import json
import socket
import threading
import time
import random
import tkinter as tk
from tkinter import ttk, messagebox
import subprocess
import re

class SOPRDeviceEmulator:

    def __init__(self, root):
        self.root = root
        self.root.title("SOPR Bluetooth Device Emulator")
        self.root.geometry("600x800")

        self.server_socket = None
        self.client_socket = None
        self.is_running = False
        self.start_time = time.time()

        self.adapters = {"ANY (00:00:00:00:00:00)": "00:00:00:00:00:00"}

        # Default values matching SOPR JSON format
        self.fw_var = tk.StringVar(value="1.01")
        self.uptime_key_var = tk.StringVar(value="run")
        self.pv_var = tk.StringVar(value="18.50")
        self.src_var = tk.StringVar(value="28.55")
        self.bat_var = tk.StringVar(value="24.15")
        self.temp_var = tk.StringVar(value="24.63")

        # Relay variables (0 or 1)
        self.re_s_var = tk.IntVar(value=1)
        self.re_r_var = tk.IntVar(value=1)
        self.re_a_var = tk.IntVar(value=1)

        # LED variables (0 or 1)
        self.led_p_var = tk.IntVar(value=1)
        self.led_s_var = tk.IntVar(value=0)
        self.led_b_var = tk.IntVar(value=1)
        self.led_a_var = tk.IntVar(value=1)
        self.led_e_var = tk.IntVar(value=0)

        # History arrays
        self.ph_history = []
        self.sh_history = []
        self.bh_history = []

        self.create_widgets()
        self.generate_random_history()
        self.find_adapters()

    def create_widgets(self):
        # Server Control Setup
        frame_setup = ttk.LabelFrame(self.root, text="Bluetooth Server Setup")
        frame_setup.pack(fill="x", padx=10, pady=5)

        ttk.Label(frame_setup, text="Adapter:").grid(row=0, column=0, padx=5, pady=5, sticky="e")
        self.combo_adapter = ttk.Combobox(frame_setup, state="readonly", width=40)
        self.combo_adapter.grid(row=0, column=1, padx=5, pady=5)
        
        btn_scan_adapters = ttk.Button(frame_setup, text="Find Adapters", command=self.find_adapters)
        btn_scan_adapters.grid(row=0, column=2, padx=5, pady=5)

        ttk.Label(frame_setup, text="Channel:").grid(row=1, column=0, padx=5, pady=5, sticky="e")
        self.combo_channel = ttk.Combobox(frame_setup, state="readonly", width=15)
        self.combo_channel.grid(row=1, column=1, sticky="w", padx=5, pady=5)
        
        btn_scan_channels = ttk.Button(frame_setup, text="Find Free Channels", command=self.find_channels)
        btn_scan_channels.grid(row=1, column=2, padx=5, pady=5)

        self.btn_start = ttk.Button(frame_setup, text="Start Server", command=self.start_server)
        self.btn_start.grid(row=2, column=0, columnspan=2, padx=5, pady=10)

        self.lbl_status = ttk.Label(frame_setup, text="Status: Stopped", foreground="red")
        self.lbl_status.grid(row=2, column=2, padx=5, pady=10)

        # Telemetry Values
        frame_vals = ttk.LabelFrame(self.root, text="Live Measurements")
        frame_vals.pack(fill="x", padx=10, pady=5)

        ttk.Label(frame_vals, text="Firmware (fw):").grid(row=0, column=0, sticky="w", padx=5, pady=2)
        ttk.Entry(frame_vals, textvariable=self.fw_var, width=10).grid(row=0, column=1, padx=5, pady=2)

        ttk.Label(frame_vals, text="Uptime JSON Key:").grid(row=0, column=2, sticky="w", padx=5, pady=2)
        ttk.Entry(frame_vals, textvariable=self.uptime_key_var, width=10).grid(row=0, column=3, padx=5, pady=2)

        ttk.Label(frame_vals, text="Solar Voltage (pv):").grid(row=1, column=0, sticky="w", padx=5, pady=2)
        ttk.Entry(frame_vals, textvariable=self.pv_var, width=10).grid(row=1, column=1, padx=5, pady=2)

        ttk.Label(frame_vals, text="24V Source Voltage (src):").grid(row=2, column=0, sticky="w", padx=5, pady=2)
        ttk.Entry(frame_vals, textvariable=self.src_var, width=10).grid(row=2, column=1, padx=5, pady=2)

        ttk.Label(frame_vals, text="Battery Voltage (bat):").grid(row=3, column=0, sticky="w", padx=5, pady=2)
        ttk.Entry(frame_vals, textvariable=self.bat_var, width=10).grid(row=3, column=1, padx=5, pady=2)

        ttk.Label(frame_vals, text="Temperature (temp):").grid(row=4, column=0, sticky="w", padx=5, pady=2)
        ttk.Entry(frame_vals, textvariable=self.temp_var, width=10).grid(row=4, column=1, padx=5, pady=2)

        # Relays and LEDs
        frame_states = ttk.LabelFrame(self.root, text="Relays & LEDs State")
        frame_states.pack(fill="x", padx=10, pady=5)

        ttk.Checkbutton(frame_states, text="Relay RE_S", variable=self.re_s_var).grid(row=0, column=0, sticky="w", padx=5)
        ttk.Checkbutton(frame_states, text="Relay RE_R", variable=self.re_r_var).grid(row=1, column=0, sticky="w", padx=5)
        ttk.Checkbutton(frame_states, text="Relay RE_A", variable=self.re_a_var).grid(row=2, column=0, sticky="w", padx=5)

        ttk.Checkbutton(frame_states, text="LED Solar", variable=self.led_p_var).grid(row=0, column=1, sticky="w", padx=5)
        ttk.Checkbutton(frame_states, text="LED Source", variable=self.led_s_var).grid(row=1, column=1, sticky="w", padx=5)
        ttk.Checkbutton(frame_states, text="LED Battery", variable=self.led_b_var).grid(row=2, column=1, sticky="w", padx=5)
        ttk.Checkbutton(frame_states, text="LED Aux", variable=self.led_a_var).grid(row=3, column=1, sticky="w", padx=5)
        ttk.Checkbutton(frame_states, text="LED Error", variable=self.led_e_var).grid(row=4, column=1, sticky="w", padx=5)

        # History Management
        frame_hist = ttk.LabelFrame(self.root, text="History Graph Generator")
        frame_hist.pack(fill="x", padx=10, pady=5)

        btn_push = ttk.Button(frame_hist, text="Push Current Values to History", command=self.push_history)
        btn_push.pack(side="left", padx=5, pady=5)

        btn_random = ttk.Button(frame_hist, text="Generate New Random History", command=self.generate_random_history)
        btn_random.pack(side="left", padx=5, pady=5)

        # Connection Log
        frame_log = ttk.LabelFrame(self.root, text="Activity Log")
        frame_log.pack(fill="both", expand=True, padx=10, pady=5)

        self.txt_log = tk.Text(frame_log, height=8)
        self.txt_log.pack(fill="both", expand=True, padx=5, pady=5)

    def log(self, message):
        timestamp = time.strftime("[%H:%M:%S] ")
        self.txt_log.insert(tk.END, timestamp + message + "\n")
        self.txt_log.see(tk.END)

    def find_adapters(self):
        self.adapters = {"ANY (00:00:00:00:00:00)": "00:00:00:00:00:00"}
        try:
            output = subprocess.check_output("ipconfig /all", text=True)
            sections = output.split("\n\n")
            for section in sections:
                if "Bluetooth" in section:
                    match = re.search(r"Physical Address[\.\s]+:\s+([0-9A-F\-]{17})", section, re.IGNORECASE)
                    if match:
                        mac = match.group(1).replace("-", ":")
                        name = section.split("\n")[0].strip()
                        self.adapters[f"{name} ({mac})"] = mac
        except Exception as e:
            self.log(f"Error reading adapters: {e}")
        
        self.combo_adapter["values"] = list(self.adapters.keys())
        if self.combo_adapter["values"]:
            self.combo_adapter.current(0)
        self.log("Scanned for local Bluetooth adapters.")

    def find_channels(self):
        selected = self.combo_adapter.get()
        if not selected:
            messagebox.showerror("Error", "Select an adapter first.")
            return
            
        mac = self.adapters[selected]
        free_channels = []
        used_channels = []
        
        self.log(f"Testing channels 1-30 on adapter {mac}...")
        
        for ch in range(1, 31):
            try:
                s = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
                s.bind((mac, ch))
                free_channels.append(str(ch))
                s.close()
            except OSError:
                used_channels.append(str(ch))
                
        self.combo_channel["values"] = free_channels
        if free_channels:
            self.combo_channel.current(0)
            
        self.log(f"Free channels: {', '.join(free_channels)}")
        if used_channels:
            self.log(f"Used channels: {', '.join(used_channels)}")

    def generate_random_history(self):
        self.ph_history = [f"{random.uniform(5.0, 45.0):.2f}" for _ in range(10)]
        self.sh_history = [f"{random.uniform(5.0, 45.0):.2f}" for _ in range(10)]
        self.bh_history = [f"{random.uniform(5.0, 45.0):.2f}" for _ in range(10)]
        self.log("Generated random history data (5V - 45V).")

    def push_history(self):
        self.ph_history.pop(0)
        self.ph_history.append(f"{float(self.pv_var.get()):.2f}")

        self.sh_history.pop(0)
        self.sh_history.append(f"{float(self.src_var.get()):.2f}")

        self.bh_history.pop(0)
        self.bh_history.append(f"{float(self.bat_var.get()):.2f}")

        self.log("Pushed live values to 10-sample history buffers.")

    def get_uptime_string(self):
        elapsed = int(time.time() - self.start_time)
        days = elapsed // 86400
        hours = (elapsed % 86400) // 3600
        minutes = (elapsed % 3600) // 60
        seconds = elapsed % 60
        return f"{days}.{hours}.{minutes}.{seconds}"

    def build_json_payload(self):
        uptime_key = str(self.uptime_key_var.get())
        
        payload = {
            "fw": str(self.fw_var.get()),
            uptime_key: self.get_uptime_string(),
            "pv": f"{float(self.pv_var.get()):.2f}",
            "src": f"{float(self.src_var.get()):.2f}",
            "bat": f"{float(self.bat_var.get()):.2f}",
            "temp": f"{float(self.temp_var.get()):.2f}",
            "re_s": int(self.re_s_var.get()),
            "re_r": int(self.re_r_var.get()),
            "re_a": int(self.re_a_var.get()),
            "led_p": int(self.led_p_var.get()),
            "led_s": int(self.led_s_var.get()),
            "led_b": int(self.led_b_var.get()),
            "led_a": int(self.led_a_var.get()),
            "led_e": int(self.led_e_var.get()),
            "pH": self.ph_history,
            "sH": self.sh_history,
            "bH": self.bh_history,
        }
        return json.dumps(payload)

    def start_server(self):
        if self.is_running:
            return

        selected_adapter = self.combo_adapter.get()
        selected_channel = self.combo_channel.get()
        
        if not selected_adapter or not selected_channel:
            messagebox.showerror("Setup Error", "You must select an adapter and a channel.")
            return

        self.is_running = True
        self.btn_start.config(state="disabled")
        self.lbl_status.config(text="Status: Waiting for Connection", foreground="orange")

        threading.Thread(target=self.run_bluetooth_listener, daemon=True).start()

    def run_bluetooth_listener(self):
        selected_adapter = self.combo_adapter.get()
        selected_channel = self.combo_channel.get()
        mac = self.adapters[selected_adapter]
        channel = int(selected_channel)
        
        try:
            self.server_socket = socket.socket(
                socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM
            )
            self.server_socket.bind((mac, channel))
            self.server_socket.listen(1)

            self.log(f"RFCOMM Bluetooth Server listening on {mac}, channel {channel}...")

            while self.is_running:
                client_sock, client_info = self.server_socket.accept()
                self.client_socket = client_sock
                self.log(f"Connected to client: {client_info}")
                self.lbl_status.config(text="Status: Connected", foreground="green")

                self.handle_client(client_sock)

        except Exception as e:
            self.log(f"Server Error: {e}")
        finally:
            self.is_running = False
            self.lbl_status.config(text="Status: Stopped", foreground="red")
            self.btn_start.config(state="normal")

    def handle_client(self, client_sock):
        try:
            while self.is_running:
                data = client_sock.recv(1024)
                if not data:
                    break

                received_str = data.decode("utf-8", errors="ignore")
                self.log(f"Received data: '{received_str.strip()}'")

                if "?" in received_str:
                    client_sock.send("SOPR\r\nR".encode("utf-8"))
                    self.log("Sent verification string (SOPR\\r\\nR).")

                if "J" in received_str or "j" in received_str:
                    json_data = self.build_json_payload()
                    client_sock.send((json_data + "\r\n").encode("utf-8"))
                    self.log("Sent SOPR JSON payload.")

        except Exception as e:
            self.log(f"Client disconnected or error: {e}")
        finally:
            client_sock.close()
            self.log("Client connection closed.")
            self.lbl_status.config(text="Status: Waiting for Connection", foreground="orange")

if __name__ == "__main__":
    root = tk.Tk()
    app = SOPRDeviceEmulator(root)
    root.mainloop()