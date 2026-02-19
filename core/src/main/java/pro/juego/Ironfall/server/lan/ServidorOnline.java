package pro.juego.Ironfall.server.lan;

import pro.juego.Ironfall.enums.TipoUnidad;
import pro.juego.Ironfall.server.GameStateServidor;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class ServidorOnline extends Thread {

    public static final int PORT = 4321;

    private DatagramSocket socket;
    private volatile boolean fin = false;

    private final Map<String, Cliente> clientes = new HashMap<>(2);
    private boolean partidaIniciada = false;

    private final GameStateServidor gameState = new GameStateServidor();

    public ServidorOnline() {
        setName("ServidorOnlineThread");
        setDaemon(false);

        try {
            socket = new DatagramSocket(PORT);
            socket.setBroadcast(true);
            System.out.println("[SERVER] UDP OK en puerto " + PORT);
        } catch (SocketException e) {
            System.out.println("[SERVER] ERROR: No pude abrir puerto " + PORT);
            e.printStackTrace();
            fin = true;
        }
    }

    @Override
    public void run() {
        if (fin) return;

        long lastTick = System.nanoTime();

        while (!fin) {

            // tick lógico simple (20 TPS)
            long now = System.nanoTime();
            float delta = (now - lastTick) / 1_000_000_000f;
            if (delta >= 0.05f) {
                lastTick = now;
                if (partidaIniciada) {
                    gameState.update(0.05f);
                }
            }

            try {
                byte[] buffer = new byte[2048];
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);

                socket.setSoTimeout(200);
                socket.receive(dp);

                String msg = new String(dp.getData(), 0, dp.getLength()).trim();
                String key = key(dp.getAddress(), dp.getPort());

                System.out.println("[SERVER] RX " + key + " -> '" + msg + "'");

                if (msg.equals("BUSCAR")) {
                    enviar("ENCONTRAR", dp.getAddress(), dp.getPort());
                    continue;
                }

                if (msg.equals("PING")) {
                    enviar("PONG", dp.getAddress(), dp.getPort());
                    continue;
                }

                if (msg.equals("Conectar")) {
                    conectar(dp);
                    continue;
                }

                Cliente emisor = clientes.get(key);
                if (emisor == null) {
                    enviar("NO_CONECTADO", dp.getAddress(), dp.getPort());
                    continue;
                }

                if (!partidaIniciada) {
                    enviar("ESPERANDO_RIVAL", dp.getAddress(), dp.getPort());
                    continue;
                }

                // Pedido de spawn
                if (msg.startsWith("SPAWN:")) {
                    String t = msg.substring("SPAWN:".length()).trim();
                    try {
                        TipoUnidad tipo = TipoUnidad.valueOf(t);

                        if (emisor.equipo == 0) gameState.getEstatua0().intentarProducir(tipo);
                        else gameState.getEstatua1().intentarProducir(tipo);

                        Cliente otro = obtenerOtro(emisor);
                        if (otro != null) {
                            enviar("RIVAL_SPAWN:" + tipo.name(), otro.ip, otro.puerto);
                        }

                        enviar("OK", emisor.ip, emisor.puerto);

                    } catch (IllegalArgumentException ex) {
                        enviar("ERROR:TipoUnidad invalido", emisor.ip, emisor.puerto);
                    }
                    continue;
                }

                // default
                enviar("ACK:" + msg, dp.getAddress(), dp.getPort());

            } catch (SocketTimeoutException ignored) {
                // normal
            } catch (IOException e) {
                if (!fin) e.printStackTrace();
            }
        }

        System.out.println("[SERVER] Cerrado.");
    }

    private void conectar(DatagramPacket dp) {

        if (clientes.size() >= 2) {
            enviar("FULL", dp.getAddress(), dp.getPort());
            return;
        }

        String key = key(dp.getAddress(), dp.getPort());
        if (clientes.containsKey(key)) {
            // ya estaba
            enviar("OK", dp.getAddress(), dp.getPort());
            return;
        }

        int equipo = (clientes.isEmpty()) ? 0 : 1;
        Cliente c = new Cliente(dp.getAddress(), dp.getPort(), equipo);
        clientes.put(key, c);

        System.out.println("[SERVER] Cliente conectado " + key + " equipo=" + equipo);
        enviar("EQUIPO:" + equipo, c.ip, c.puerto);

        if (clientes.size() == 2 && !partidaIniciada) {
            partidaIniciada = true;
            System.out.println("[SERVER] START -> ambos");
            for (Cliente cli : clientes.values()) {
                enviar("START", cli.ip, cli.puerto);
            }
        } else {
            enviar("ESPERANDO_RIVAL", c.ip, c.puerto);
        }
    }

    private Cliente obtenerOtro(Cliente emisor) {
        for (Cliente c : clientes.values()) {
            if (c != emisor) return c;
        }
        return null;
    }

    private void enviar(String msg, InetAddress ip, int port) {
        try {
            byte[] data = msg.getBytes();
            DatagramPacket out = new DatagramPacket(data, data.length, ip, port);
            socket.send(out);
            System.out.println("[SERVER] TX " + ip.getHostAddress() + ":" + port + " <- '" + msg + "'");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String key(InetAddress ip, int port) {
        return ip.getHostAddress() + ":" + port;
    }

    public void cerrar() {
        fin = true;
        if (socket != null && !socket.isClosed()) socket.close();
        interrupt();
    }

    private static class Cliente {
        InetAddress ip;
        int puerto;
        int equipo;

        Cliente(InetAddress ip, int puerto, int equipo) {
            this.ip = ip;
            this.puerto = puerto;
            this.equipo = equipo;
        }
    }
}
