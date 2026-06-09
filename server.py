import socket
import threading
import struct

PORT = 8888
active_clients = {}
networks = {}

def extract_destination_ip(packet):
    try:
        dest_ip_bytes = packet[16:20]
        return socket.inet_ntoa(dest_ip_bytes)
    except Exception:
        return None

def handle_peer(conn, addr):
    print(f"[*] Connection received from physical IP: {addr}")
    net_key = None
    virtual_ip = None
    try:
        handshake_data = b""
        while b"\n" not in handshake_data:
            chunk = conn.recv(1024)
            if not chunk:
                return
            handshake_data += chunk
            
        line = handshake_data.decode('utf-8').strip()
        parts = line.split(":")
        if len(parts) < 3:
            return
        
        role, net_name, net_pass = parts[0], parts[1], parts[2]
        net_key = f"{net_name}:{net_pass}"
        
        if role == "C":
            virtual_ip = "10.8.0.1"
            print(f"[*] Host registered for VLAN [{net_name}] with IP {virtual_ip}")
        else:
            virtual_ip = "10.8.0.2"
            print(f"[*] Client registered for VLAN [{net_name}] with IP {virtual_ip}")

        if net_key not in networks:
            networks[net_key] = []
        networks[net_key].append(virtual_ip)
        active_clients[virtual_ip] = conn

        while True:
            size_header = conn.recv(4)
            if not size_header or len(size_header) < 4:
                break
            
            packet_len = struct.unpack(">I", size_header)[0]
            packet_data = b""
            while len(packet_data) < packet_len:
                chunk = conn.recv(packet_len - len(packet_data))
                if not chunk:
                    break
                packet_data += chunk

            if len(packet_data) != packet_len:
                break

            dest_ip = extract_destination_ip(packet_data)
            if not dest_ip:
                continue

            is_broadcast = dest_ip.endswith(".255") or dest_ip == "255.255.255.255"

            if is_broadcast:
                for peer_ip in networks.get(net_key, []):
                    if peer_ip != virtual_ip and peer_ip in active_clients:
                        try:
                            active_clients[peer_ip].sendall(struct.pack(">I", packet_len) + packet_data)
                        except Exception:
                            pass
            else:
                if dest_ip in active_clients:
                    try:
                        active_clients[dest_ip].sendall(struct.pack(">I", packet_len) + packet_data)
                    except Exception:
                        pass

    except Exception as e:
        print(f"[-] Connection handling error for {addr}: {e}")
    finally:
        print(f"[*] Connection terminated with client {virtual_ip}")
        if virtual_ip in active_clients:
            del active_clients[virtual_ip]
        if net_key in networks and virtual_ip in networks[net_key]:
            networks[net_key].remove(virtual_ip)
        conn.close()

def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", PORT))
    server.listen(10)
    print(f"[*] java's VLAN Coordination Server active on port {PORT}...")

    while True:
        conn, addr = server.accept()
        t = threading.Thread(target=handle_peer, args=(conn, addr))
        t.daemon = True
        t.start()

if __name__ == "__main__":
    main()
