package pro.juego.Ironfall.server.lan;

import pro.juego.Ironfall.server.GameStateServidor;
import pro.juego.Ironfall.enums.TipoUnidad;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServidorIronfallLAN extends Thread {

    private static final int PORT = 4321;
    private static final int MAX_CLIENTES = 2;
    private static final float TICK_RATE = 1f / 20f;

    private DatagramSocket socket;
    private Cliente[] clientes = new Cliente[MAX_CLIENTES];

    private GameStateServidor gameState = new GameStateServidor();

    private AtomicBoolean running = new AtomicBoolean(true);
    private boolean partidaIniciada = false;

    public ServidorIronfallLAN() {
        try {
            socket = new DatagramSocket(PORT);
            socket.setSoTimeout(1);
            System.out.println("[SERVER] Ironfall LAN escuchando en puerto " + PORT);
        } catch (SocketException e) {
            throw new RuntimeException("Puerto ocupado");
        }
    }

    @Override
    public void run() {

        long ultimoTick = System.nanoTime();

        while (running.get()) {

            recibirMensajes();

            long ahora = System.nanoTime();
            float delta = (ahora - ultimoTick) / 1_000_000_000f;

            if (delta >= TICK_RATE) {

                if (partidaIniciada) {
                    gameState.update(TICK_RATE);
                    enviarEstado();
                }

                ultimoTick = ahora;
            }
        }
    }

    private void recibirMensajes() {
        try {
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            String mensaje = new String(packet.getData(), 0, packet.getLength()).trim();

            if (mensaje.equals("BUSCAR")) {
                enviar("ENCONTRAR", packet.getAddress(), packet.getPort());
                return;
            }

            if (mensaje.equals("CONECTAR")) {
                conectarCliente(packet);
                return;
            }

            if (mensaje.startsWith("SPAWN:")) {
                Cliente c = obtenerCliente(packet);
                if (c == null) return;

                String tipoStr = mensaje.substring(6);
                TipoUnidad tipo = TipoUnidad.valueOf(tipoStr);

                if (c.equipo == 0)
                    gameState.getEstatua0().intentarProducir(tipo);
                else
                    gameState.getEstatua1().intentarProducir(tipo);

                return;
            }

        } catch (SocketTimeoutException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void conectarCliente(DatagramPacket packet) {

        int slot = buscarSlotLibre();
        if (slot == -1) {
            enviar("FULL", packet.getAddress(), packet.getPort());
            return;
        }

        Cliente nuevo = new Cliente(packet, slot);
        clientes[slot] = nuevo;

        enviar("EQUIPO:" + slot, nuevo.ip, nuevo.port);
        System.out.println("[SERVER] Cliente conectado equipo " + slot);

        if (clientes[0] != null && clientes[1] != null) {
            partidaIniciada = true;
            enviarATodos("START");
            System.out.println("[SERVER] Partida iniciada");
        }
    }

    private void enviarEstado() {

        StringBuilder sb = new StringBuilder();

        sb.append("STATE|");

        sb.append("S0,")
                .append(gameState.getEstatua0().getVida()).append("|");

        sb.append("S1,")
                .append(gameState.getEstatua1().getVida()).append("|");

        gameState.getUnidadesEquipo0().forEach(u ->
                sb.append("U,0,")
                        .append(u.getX()).append(",")
                        .append(u.getVida()).append("|"));

        gameState.getUnidadesEquipo1().forEach(u ->
                sb.append("U,1,")
                        .append(u.getX()).append(",")
                        .append(u.getVida()).append("|"));

        enviarATodos(sb.toString());
    }

    private void enviar(String msg, InetAddress ip, int port) {
        try {
            byte[] data = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, ip, port);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void enviarATodos(String msg) {
        for (Cliente c : clientes) {
            if (c != null)
                enviar(msg, c.ip, c.port);
        }
    }

    private Cliente obtenerCliente(DatagramPacket p) {
        for (Cliente c : clientes) {
            if (c != null && c.esEste(p)) return c;
        }
        return null;
    }

    private int buscarSlotLibre() {
        for (int i = 0; i < MAX_CLIENTES; i++) {
            if (clientes[i] == null) return i;
        }
        return -1;
    }

    public void cerrar() {
        running.set(false);
        socket.close();
    }

    private static class Cliente {
        InetAddress ip;
        int port;
        int equipo;

        Cliente(DatagramPacket p, int equipo) {
            this.ip = p.getAddress();
            this.port = p.getPort();
            this.equipo = equipo;
        }

        boolean esEste(DatagramPacket p) {
            return ip.equals(p.getAddress()) && port == p.getPort();
        }
    }
}
