package com.example.unixsocketchanneltest;

import java.io.FileDescriptor;


public class IoUtils {

    public static void setBlocking(FileDescriptor fd, boolean blocking) {
        // TODO(erdal): either call this https://android.googlesource.com/platform/libcore/+/jb-mr2-release/luni/src/main/java/libcore/io/IoUtils.java
        // https://stackoverflow.com/questions/24578243/cannot-resolve-symbol-ioutils
        // or this: https://android.googlesource.com/platform/libcore/+/c8d9ea6/luni/src/main/java/android/system/Os.java#115

    }
}
