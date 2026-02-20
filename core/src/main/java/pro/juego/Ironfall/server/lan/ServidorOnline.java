package pro.juego.Ironfall.server.lan;

import pro.juego.Ironfall.enums.TipoUnidad;
import pro.juego.Ironfall.server.GameStateServidor;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ServidorOnline extends Thread {

    public static final int PORT = 4321;

    private static final float FIXED_DT = 0.05f; // 20 TPS
    private float accumulator = 0f;

    private DatagramSocket socket;
    private volatile boolean fin = false;

    private final Map<String, Cliente> clientes = new HashMap<>(2);
    private boolean partidaIniciada = false;

    // ❗ ya NO final porque vamos a resetear
    private GameStateServidor gameState = new GameStateServidor();

    // Sync oro periódico
    private float timerSyncOro = 0f;
    private static final float INTERVALO_SYNC_ORO = 0.5f;

    private static final int ORO_INICIAL_TEST = 500;

    // ✅ limpieza por timeout
    private static final long CLIENT_TIMEOUT_MS = 15000;

    // ✅ game over (repetimos mensaje por UDP)
    private boolean gameOverEnProceso = false;
    private float timerGameOver = 0f;
    private static final float DURACION_REENVIO_GAME_OVER = 1.0f; // 1s reenviando

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
            // TICK LÓGICO REAL
            // ======================
            long now = System.nanoTime();
            float frameDelta = (now - lastTime) / 1_000_000_000f;
            lastTime = now;

            if (frameDelta > 0.25f) frameDelta = 0.25f;

            // limpiar clientes colgados (crash / cierre sin DISCONNECT)
            limpiarClientesPorTimeout();

            if (partidaIniciada) {
                accumulator += frameDelta;

                while (accumulator >= FIXED_DT) {

                    // si ya estamos en game over, solo reenviamos y reseteamos
                    if (gameOverEnProceso) {
                        tickGameOver(FIXED_DT);
                        accumulator -= FIXED_DT;
                        continue;
                    }

                    gameState.update(FIXED_DT);

                    // oro sync
                    timerSyncOro += FIXED_DT;
                    if (timerSyncOro >= INTERVALO_SYNC_ORO) {
                        timerSyncOro = 0f;
                        enviarATodos("ORO:0:" + gameState.getEstatua0().getOro());
                        enviarATodos("ORO:1:" + gameState.getEstatua1().getOro());
                    }

                    // ✅ detecta fin y arranca proceso
                    if (gameState.estaTerminado()) {
                        gameOverEnProceso = true;
                        timerGameOver = 0f;

                        int ganador = gameState.getGanador();
                        System.out.println("[SERVER] GAME OVER! Ganador=" + ganador);

                        // primer envío inmediato
                        enviarATodos("GAME_OVER:" + ganador);
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

                socket.setSoTimeout(20);
                socket.receive(dp);

                String msg = new String(dp.getData(), 0, dp.getLength()).trim();
                String key = key(dp.getAddress(), dp.getPort());

                // actualizar lastSeen si está registrado
                Cliente cReg = clientes.get(key);
                if (cReg != null) cReg.lastSeenMs = System.currentTimeMillis();

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

                // ✅ el cliente avisa que se va
                if (msg.equals("DISCONNECT")) {
                    desconectar(key);
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

                // si estamos en game over, ignoramos spawns (la partida ya terminó)
                if (gameOverEnProceso) {
                    enviar("ACK:GAME_OVER", dp.getAddress(), dp.getPort());
                    continue;
                }

                // SPAWN
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

                    // sync oro inmediato
                    enviarATodos("ORO:0:" + gameState.getEstatua0().getOro());
                    enviarATodos("ORO:1:" + gameState.getEstatua1().getOro());
                    continue;
                }

                enviar("ACK:" + msg, dp.getAddress(), dp.getPort());

            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                if (!fin) e.printStackTrace();
            }
        }

        System.out.println("[SERVER] Cerrado.");
    }

    private void tickGameOver(float dt) {
        timerGameOver += dt;

        // reenviamos GAME_OVER durante 1s para que UDP no lo pierda
        if (timerGameOver <= DURACION_REENVIO_GAME_OVER) {
            int ganador = gameState.getGanador();
            enviarATodos("GAME_OVER:" + ganador);
            return;
        }

        // ✅ después reseteamos todo
        resetearPartida();
    }

    private void resetearPartida() {
        System.out.println("[SERVER] Reseteando partida y liberando clientes...");

        partidaIniciada = false;
        gameOverEnProceso = false;
        timerGameOver = 0f;
        accumulator = 0f;
        timerSyncOro = 0f;

        // liberar clientes para que puedan reconectar
        clientes.clear();

        // nuevo estado
        gameState = new GameStateServidor();
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

    private void desconectar(String key) {
        Cliente c = clientes.remove(key);
        System.out.println("[SERVER] DISCONNECT " + key + " -> removido=" + (c != null));

        // si alguien se fue, paramos la partida y reseteamos para evitar estados raros
        if (partidaIniciada) {
            resetearPartida();
        }
    }

    private void limpiarClientesPorTimeout() {
        if (clientes.isEmpty()) return;

        long now = System.currentTimeMillis();
        boolean removioAlguien = false;

        Iterator<Map.Entry<String, Cliente>> it = clientes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Cliente> e = it.next();
            Cliente c = e.getValue();
            if (now - c.lastSeenMs > CLIENT_TIMEOUT_MS) {
                System.out.println("[SERVER] TIMEOUT cliente " + e.getKey() + " -> removido");
                it.remove();
                removioAlguien = true;
            }
        }

        if (removioAlguien && partidaIniciada) {
            resetearPartida();
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
            // System.out.println("[SERVER] TX " + ip.getHostAddress() + ":" + port + " <- '" + msg + "'");
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
        long lastSeenMs;

        Cliente(InetAddress ip, int puerto, int equipo) {
            this.ip = ip;
            this.puerto = puerto;
            this.equipo = equipo;
            this.lastSeenMs = System.currentTimeMillis();
        }
    }
}