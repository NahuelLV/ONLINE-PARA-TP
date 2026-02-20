package pro.juego.Ironfall.server.lan;

import pro.juego.Ironfall.enums.TipoUnidad;
import pro.juego.Ironfall.server.GameStateServidor;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class ServidorOnline extends Thread {

    public static final int PORT = 4321;

    private static final float FIXED_DT = 0.05f; // 20 TPS
    private float accumulator = 0f;

    private DatagramSocket socket;
    private volatile boolean fin = false;

    private final Map<String, Cliente> clientes = new HashMap<>(2);
    private boolean partidaIniciada = false;

    private final GameStateServidor gameState = new GameStateServidor();

    // ✅ Sync oro periódico (aunque nadie spawnee)
    private float timerSyncOro = 0f;
    private static final float INTERVALO_SYNC_ORO = 0.5f; // 2 veces por segundo

    private static final int ORO_INICIAL_TEST = 500;

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

        long lastTime = System.nanoTime();

        while (!fin) {

            // ======================
            // ✅ TICK LÓGICO REAL (con acumulador)
            // ======================
            long now = System.nanoTime();
            float frameDelta = (now - lastTime) / 1_000_000_000f;
            lastTime = now;

            // (evita explosiones si el server se freezea)
            if (frameDelta > 0.25f) frameDelta = 0.25f;

            if (partidaIniciada) {
                accumulator += frameDelta;

                while (accumulator >= FIXED_DT) {
                    gameState.update(FIXED_DT);

                    timerSyncOro += FIXED_DT;
                    if (timerSyncOro >= INTERVALO_SYNC_ORO) {
                        timerSyncOro = 0f;
                        enviarATodos("ORO:0:" + gameState.getEstatua0().getOro());
                        enviarATodos("ORO:1:" + gameState.getEstatua1().getOro());
                    }

                    accumulator -= FIXED_DT;
                }
            }

            // ======================
            // RED RX
            // ======================
            try {
                byte[] buffer = new byte[2048];
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);

                // ✅ timeout chico para no frenar el tick
                socket.setSoTimeout(20);
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

                // =========================
                // SPAWN
                // OK:   SPAWN_OK:<equipo>:<tipo>
                // FAIL: SPAWN_FAIL:<motivo>
                // =========================
                if (msg.startsWith("SPAWN:")) {
                    String t = msg.substring("SPAWN:".length()).trim();

                    TipoUnidad tipo;
                    try {
                        tipo = TipoUnidad.valueOf(t);
                    } catch (IllegalArgumentException ex) {
                        enviar("SPAWN_FAIL:TipoUnidad invalido", emisor.ip, emisor.puerto);
                        continue;
                    }

                    boolean ok;
                    if (emisor.equipo == 0) ok = gameState.getEstatua0().intentarProducir(tipo);
                    else ok = gameState.getEstatua1().intentarProducir(tipo);

                    if (!ok) {
                        enviar("SPAWN_FAIL:Sin oro", emisor.ip, emisor.puerto);
                        continue;
                    }

                    enviarATodos("SPAWN_OK:" + emisor.equipo + ":" + tipo.name());

                    // sync oro inmediato también
                    enviarATodos("ORO:0:" + gameState.getEstatua0().getOro());
                    enviarATodos("ORO:1:" + gameState.getEstatua1().getOro());

                    continue;
                }

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

            gameState.getEstatua0().setOro(ORO_INICIAL_TEST);
            gameState.getEstatua1().setOro(ORO_INICIAL_TEST);

            System.out.println("[SERVER] START -> ambos");
            enviarATodos("START");

            enviarATodos("ORO:0:" + gameState.getEstatua0().getOro());
            enviarATodos("ORO:1:" + gameState.getEstatua1().getOro());

        } else {
            enviar("ESPERANDO_RIVAL", c.ip, c.puerto);
        }
    }

    private void enviarATodos(String msg) {
        for (Cliente c : clientes.values()) {
            enviar(msg, c.ip, c.puerto);
        }
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