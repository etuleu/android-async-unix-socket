package com.example.unixsocketchanneltest;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

// loosely inspired from
// jnr-unix:https://github.com/jnr/jnr-unixsocket/blob/master/src/main/java/jnr/unixsocket/UnixSocketChannel.java
// and LocalSocketImpl https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/net/LocalSocketImpl.java
// and junixsocket https://github.com/kohlschutter/junixsocket/blob/master/junixsocket-common/src/main/java/org/newsclub/net/unix/AFUNIXSocketImpl.java

// TODO(erdal): write tests
// TODO(erdal): other features? pair, ?
// TODO(erdal): what android API level does this support?
// TODO(erdal): performance tests, memory leaks?
// TODO(erdal): doc strings
// TODO(erdal): questions: read/write binary, text, encoding, network order, buffering

public class UnixSocketChannel extends SocketChannel {

    enum State {
        UNINITIALIZED,
        CONNECTED,
        IDLE,
        CONNECTING,
    }

    private State state = State.UNINITIALIZED;
    /** null if closed or not yet created */
    private FileDescriptor fd;
    private SocketAddress remoteAddress;

    public UnixSocketChannel(SelectorProvider provider) throws IOException {
        super(provider);
        Util.print("constructor");
        create();
    }

    /**
     * Creates a socket in the underlying OS.
     *
     * @throws IOException
     */
    public void create() throws IOException {
        if (fd != null) {
            throw new IOException("LocalSocketImpl already has an fd");
        }

        try {
            fd = Os.socket(OsConstants.AF_UNIX, OsConstants.SOCK_STREAM, 0);
        } catch (ErrnoException e) {
            // TODO(erdal) rethrow using the last errno
            throw new IOException(e);
        }
    }

    @Override
    public SocketChannel bind(SocketAddress local) {
        Util.print("bind");
        return null;
    }

    @Override
    public <T> SocketChannel setOption(SocketOption<T> name, T value)  {
        Util.print("setOption " + name + " " + value);
        return null;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) {
        Util.print("getOption " + name);
        return null;
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        Util.print("supportedOptions");
        return null;
    }

    @Override
    public SocketChannel shutdownInput() throws IOException {
        Util.print("shutdownInput");
        if (fd == null) {
            throw new IOException("socket not created");
        }

        try {
            Os.shutdown(fd, OsConstants.SHUT_RD);
        } catch (ErrnoException e) {
            throw new IOException(e);
        }

        return this;
    }

    @Override
    public SocketChannel shutdownOutput() throws IOException {
        if (fd == null) {
            throw new IOException("socket not created");
        }

        try {
            Os.shutdown(fd, OsConstants.SHUT_WR);
        } catch (ErrnoException e) {
            throw new IOException(e);
        }

        return this;
    }

    @Override
    public Socket socket() {
        Util.print("socket");
        return null;
    }

    @Override
    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    @Override
    public boolean isConnectionPending() {
        return state == State.CONNECTING;
    }

    @Override
    public boolean connect(SocketAddress remote) throws IOException {
        Util.print("connect " + remote);
        remoteAddress = remote;
        if(doConnect(remote)) {
            state = State.CONNECTED;
            return true;
        }

        state = State.CONNECTING;
        return false;
    }

    private boolean doConnect(SocketAddress remote) throws IOException {
        Util.print("doConnect " + remote);

        try {
            InetSocketAddress addr = (InetSocketAddress) remote;
            Os.connect(fd, addr.getAddress(), 0);
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.EAGAIN) {
                return false;
            }
            throw new IOException(e);
        }

        return true;
    }

    @Override
    public boolean finishConnect() throws IOException {
        Util.print("finishConnect");
        if (state == State.CONNECTED) {
            return true;
        }
        if (state == State.CONNECTING) {
            if (!doConnect(remoteAddress)) {
                return false;
            }
            state = State.CONNECTED;
            return true;
        }

        throw new IllegalStateException("socket is not waiting for connect to complete");
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        Util.print("getRemoteAddress");
        return remoteAddress;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        Util.print("read " + dst);
        if(!isConnected()) {
            throw new ClosedChannelException();
        }

        ByteBuffer buffer = ByteBuffer.allocate(dst.remaining());

        try {
            int n = Os.read(fd, buffer);
            if (n == 0) {
                return -1;
            }
            if (n == -1) {
                throw new IOException(e);
            }
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.EAGAIN) {
                return 0;
            }
        }

        buffer.flip();
        dst.put(buffer);

        return n;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        Util.print("read " + dsts + " " + offset + " " + length);
        if(!isConnected()) {
            throw new ClosedChannelException();
        }

        long total = 0;

        for (int i = 0; i < length; i++) {
            ByteBuffer dst = dsts[offset + i];
            long read = read(dst);
            if (read == -1) {
                return read;
            }
            total += read;
        }

        return total;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        Util.print("write " + src);
        if(!isConnected()) {
            throw new ClosedChannelException();
        }

        int r = src.remaining();
        ByteBuffer buffer = ByteBuffer.allocate(r);
        buffer.put(src);
        buffer.position(0);

        int n = -1;
        int errno = 0;
        String errorMessage = null;

        try {
            n = Os.write(fd, buffer);
        } catch (ErrnoException e) {
            errno = e.errno;
            errorMessage = e.getMessage();
        }

        if (n >= 0 ) {
            if (n < r) {
                src.position(src.position() - (r-n));
            }
        } else {
            if (errno == OsConstants.EAGAIN) {
                src.position(src.position() - r);
                return 0;
            } else {
                throw new IOException(errorMessage);
            }
        }

        return n;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        Util.print("write " + srcs + " " + offset + " " + length);
        if(!isConnected()) {
            throw new ClosedChannelException();
        }

        long result = 0;
        int index = 0;

        for (index = offset; index < length; index++) {
            result += write(srcs[index]);
        }

        return result;
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        Util.print("getLocalAddress");
        // TODO(erdal): is this even called? when?
        return null;
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        Util.print("implCloseSelectableChannel");
        if (fd == null) {
            return;
        }

        try {
            Os.close(fd);
        } catch (ErrnoException e) {
            throw new IOException(e);
        }
        fd = null;
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        Util.print("implConfigureBlocking " + block);
        IoUtils.setBlocking(fd, block);
    }
}
