package org.sovereignos.airpods.probe;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.sovereignos.airpods.validation.AapCodec;
import org.sovereignos.airpods.validation.AirPodsMessage;
import org.sovereignos.airpods.validation.AirPodsValidationException;
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode;

/**
 * Capture tool. Start with, for example:
 * adb shell am start -n org.sovereignos.airpods.probe/.ProbeActivity --es address AA:BB:CC:DD:EE:FF
 *   --ei seconds 30 --es order hcn --es cmds mode:3,ca:1
 * order: h handshake, c host capabilities, n request notifications, in the given order, 200 ms apart.
 * cmds: sent from second 8 on, 3 s apart: mode:1..3 (off, anc, transparency), ca:0|1, ear:0|1, raw:<hex>.
 * Everything goes to logcat tag AirPodsProbe. A frame is printed in hex only if it is a kind the
 * codec acts on (opcode 4, 6, 9) or has no standard header; a device information frame (opcode 0x1D,
 * serial numbers) is never printed, only its length.
 */
public final class ProbeActivity extends Activity {
    private static final String TAG = "AirPodsProbe";
    private static final int PSM = 0x1001;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String address = getIntent().getStringExtra("address");
        int seconds = getIntent().getIntExtra("seconds", 20);
        String order = getIntent().getStringExtra("order");
        String cmds = getIntent().getStringExtra("cmds");
        if (address == null) {
            Log.e(TAG, "missing --es address");
            finish();
            return;
        }
        new Thread(() -> {
            try {
                run(address, seconds, order == null ? "hcn" : order, cmds == null ? "" : cmds);
            } finally {
                runOnUiThread(this::finish);
            }
        }, "probe").start();
    }

    private static String hex(byte[] b, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(String.format("%02X ", b[i] & 0xFF));
        return sb.toString().trim();
    }

    private void run(String address, int seconds, String order, String cmds) {
        long t0 = SystemClock.elapsedRealtime();
        BluetoothAdapter adapter = getSystemService(BluetoothManager.class).getAdapter();
        BluetoothDevice device = adapter.getRemoteDevice(address);
        BluetoothSocket socket;
        try {
            Method m = BluetoothDevice.class.getMethod("createL2capSocket", int.class);
            socket = (BluetoothSocket) m.invoke(device, PSM);
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "createL2capSocket not reachable: " + e.getClass().getSimpleName());
            return;
        }
        try {
            socket.connect();
            Log.i(TAG, "connected after " + (SystemClock.elapsedRealtime() - t0) + " ms, maxRx=" + socket.getMaxReceivePacketSize());
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            for (char c : order.toCharArray()) {
                byte[] f = c == 'h' ? AapCodec.handshake() : c == 'c' ? AapCodec.hostCapabilities() : AapCodec.requestNotifications();
                out.write(f);
                out.flush();
                Log.i(TAG, "+" + (SystemClock.elapsedRealtime() - t0) + " ms sent " + c + " " + hex(f, f.length));
                SystemClock.sleep(200);
            }
            Thread cmdThread = new Thread(() -> sendCommands(out, cmds, t0), "probe-cmds");
            cmdThread.start();
            // read() blocks; a watchdog closes the socket when the time is up, which ends the loop.
            new Thread(() -> {
                SystemClock.sleep(seconds * 1000L);
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // closed
                }
            }, "probe-watchdog").start();
            byte[] buf = new byte[2048];
            int frames = 0;
            while (true) {
                int n = in.read(buf);
                if (n < 0) {
                    Log.i(TAG, "stream ended");
                    break;
                }
                frames++;
                logFrame(t0, buf, n);
            }
            Log.i(TAG, "done, frames=" + frames);
        } catch (IOException e) {
            Log.e(TAG, "io: " + e.getClass().getSimpleName() + " " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // closed
            }
        }
    }

    private void logFrame(long t0, byte[] buf, int n) {
        long t = SystemClock.elapsedRealtime() - t0;
        boolean standard = n >= 6 && buf[0] == 4 && buf[1] == 0 && buf[2] == 4 && buf[3] == 0;
        int opcode = standard ? (buf[4] & 0xFF) | ((buf[5] & 0xFF) << 8) : -1;
        boolean showHex = !standard ? true : (opcode == 4 || opcode == 6 || opcode == 9);
        String decoded;
        try {
            decoded = AapCodec.decode(Arrays.copyOf(buf, n)).toString();
        } catch (AirPodsValidationException e) {
            decoded = "REJECTED " + e.getMessage();
        }
        Log.i(TAG, "+" + t + " ms recv len=" + n + " opcode=" + (opcode < 0 ? "none" : String.format("0x%04X", opcode))
                + (showHex ? " hex=" + hex(buf, Math.min(n, 32)) : "") + " -> " + decoded);
    }

    private void sendCommands(OutputStream out, String cmds, long t0) {
        if (cmds.isEmpty()) return;
        SystemClock.sleep(8000);
        for (String c : cmds.split(",")) {
            String[] kv = c.split(":");
            byte[] f;
            if (kv[0].equals("mode")) {
                f = AapCodec.setListeningMode(new ListeningMode[] {null, ListeningMode.OFF, ListeningMode.ANC, ListeningMode.TRANSPARENCY}[Integer.parseInt(kv[1])]);
            } else if (kv[0].equals("ca")) {
                f = AapCodec.setConversationAwareness(kv[1].equals("1"));
            } else if (kv[0].equals("ear")) {
                f = AapCodec.setEarDetection(kv[1].equals("1"));
            } else if (kv[0].equals("raw")) {
                // raw:<hex without spaces>, for example raw:04000400090034010000 00 (a control frame)
                String h = kv[1];
                f = new byte[h.length() / 2];
                for (int i = 0; i < f.length; i++) f[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
            } else {
                continue;
            }
            try {
                out.write(f);
                out.flush();
                Log.i(TAG, "+" + (SystemClock.elapsedRealtime() - t0) + " ms sent cmd " + c + " " + hex(f, f.length));
            } catch (IOException e) {
                Log.e(TAG, "cmd io: " + e.getClass().getSimpleName());
                return;
            }
            SystemClock.sleep(3000);
        }
    }
}
