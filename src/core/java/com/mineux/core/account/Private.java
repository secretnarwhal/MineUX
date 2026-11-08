package com.mineux.core.account;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Files only their owner can read: {@code rw-------} where there are POSIX permissions, and an
 * ACL of the owner alone (no inherited entries) on Windows. Best effort: a file system with
 * neither keeps what it has.
 */
final class Private {

    private static final Set<PosixFilePermission> OWNER = PosixFilePermissions.fromString("rw-------");

    private Private() {}

    /** An empty file, private from the moment it exists where the file system allows that. */
    static void create(Path file) throws IOException {
        Files.deleteIfExists(file);
        if (posix(file)) {
            try {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(OWNER));
            } catch (FileAlreadyExistsException e) {
                // Raced by another write of ours: restricted below all the same.
            }
        } else {
            Files.createFile(file);
        }
        restrict(file);
    }

    /** Takes every permission but the owner's away. */
    static void restrict(Path file) {
        try {
            if (posix(file)) {
                Files.setPosixFilePermissions(file, OWNER);
                return;
            }
            AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
            FileOwnerAttributeView owners = Files.getFileAttributeView(file, FileOwnerAttributeView.class);
            if (acl == null || owners == null) return;
            UserPrincipal owner = owners.getOwner();
            AclEntry entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build();
            acl.setAcl(List.of(entry));
        } catch (IOException | RuntimeException e) {
            // Best effort: the file is in the user's own game folder either way.
        }
    }

    private static boolean posix(Path file) {
        Path dir = file.toAbsolutePath().getParent();
        return dir != null && Files.getFileAttributeView(dir, PosixFileAttributeView.class) != null;
    }
}
