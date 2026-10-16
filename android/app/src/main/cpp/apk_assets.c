// Lets the Butterscotch runtime read the bundled game straight out of the APK, so it never has to be
// copied to internal storage (which would double the app's footprint).
//
// Any path starting with APK_ASSETS_PREFIX ("/@assets/") is served from the APK's assets via AAssetManager:
//   /@assets/game/data.win  ->  assets/game/data.win
//
// The runtime itself only knows about fopen/stat, so instead of patching it we link with
// -Wl,--wrap=fopen,--wrap=fopen64,--wrap=stat,--wrap=fileno,--wrap=fstat (see inject.cmake): every call inside
// libbutterscotch.so lands here first, and anything that isn't a virtual path falls through to the real libc function.
// fopen on a virtual path returns a funopen64() FILE*, so fread/fseek/ftell/setvbuf all work as usual.
// Such a FILE has no real fd, but miniaudio sizes files with fstat(fileno(f)), so virtual FILEs get a fake fd
// that only our fstat understands.
// Virtual paths are read-only; directory listing (opendir) is not supported for them.

// funopen64 is only declared with BSD extensions enabled
#define _BSD_SOURCE

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <errno.h>
#include <limits.h>
#include <pthread.h>
#include <stdbool.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

#define APK_ASSETS_PREFIX "/@assets/"

FILE* __real_fopen(const char* path, const char* mode);
FILE* __real_fopen64(const char* path, const char* mode);
int __real_stat(const char* path, struct stat* st);
int __real_fileno(FILE* f);
int __real_fstat(int fd, struct stat* st);

static AAssetManager* gAssetManager = NULL;
static jobject gAssetManagerRef = NULL;

JNIEXPORT void JNICALL Java_net_perfectdreams_butterscotch_android_ApkAssets_init(JNIEnv* env, jclass cls, jobject assetManager) {
    (void) cls;
    if (gAssetManagerRef != NULL) return;
    // Keep the Java AssetManager alive for as long as we hold the native pointer
    gAssetManagerRef = (*env)->NewGlobalRef(env, assetManager);
    gAssetManager = AAssetManager_fromJava(env, gAssetManagerRef);
}

// Writes the asset-relative path for a virtual path into out and returns it, or returns NULL if it isn't one.
// AAssetManager doesn't understand "." / ".." / "//" (games do things like "chapter1/../mus/x.ogg"), so those are resolved here.
// A trailing slash is dropped.
static const char* toAssetPath(const char* path, char out[PATH_MAX]) {
    if (gAssetManager == NULL || path == NULL) return NULL;
    size_t prefixLen = strlen(APK_ASSETS_PREFIX);
    if (strncmp(path, APK_ASSETS_PREFIX, prefixLen) != 0) return NULL;

    size_t outLen = 0;
    const char* p = path + prefixLen;
    while (*p != '\0') {
        const char* end = strchr(p, '/');
        size_t segLen = end != NULL ? (size_t) (end - p) : strlen(p);

        if (segLen == 0 || (segLen == 1 && p[0] == '.')) {
            // Skip empty and "." segments
        } else if (segLen == 2 && p[0] == '.' && p[1] == '.') {
            // Pop the last segment (going above the assets root just clamps to it)
            while (outLen > 0 && out[outLen - 1] != '/') outLen--;
            if (outLen > 0) outLen--;
        } else {
            if (outLen + (outLen > 0 ? 1 : 0) + segLen >= PATH_MAX) return NULL;
            if (outLen > 0) out[outLen++] = '/';
            memcpy(out + outLen, p, segLen);
            outLen += segLen;
        }

        if (end == NULL) break;
        p = end + 1;
    }
    out[outLen] = '\0';
    return out;
}

// ===[ Open virtual files ]===
// Each open virtual FILE takes a slot; its fake fd is FAKE_FD_BASE + slot. Audio is loaded from other threads, hence the lock.

#define MAX_VIRTUAL_FILES 256
#define FAKE_FD_BASE 0x40000000

typedef struct {
    AAsset* asset;
    FILE* file;
} VirtualFile;

static VirtualFile* gVirtualFiles[MAX_VIRTUAL_FILES];
static pthread_mutex_t gVirtualFilesLock = PTHREAD_MUTEX_INITIALIZER;

static void registerVirtualFile(VirtualFile* vf) {
    pthread_mutex_lock(&gVirtualFilesLock);
    for (int i = 0; MAX_VIRTUAL_FILES > i; i++) {
        if (gVirtualFiles[i] == NULL) {
            gVirtualFiles[i] = vf;
            break;
        }
    }
    // If every slot is taken the FILE still works, it just has no fd (fileno returns -1 like any funopen FILE)
    pthread_mutex_unlock(&gVirtualFilesLock);
}

static void unregisterVirtualFile(VirtualFile* vf) {
    pthread_mutex_lock(&gVirtualFilesLock);
    for (int i = 0; MAX_VIRTUAL_FILES > i; i++) {
        if (gVirtualFiles[i] == vf) {
            gVirtualFiles[i] = NULL;
            break;
        }
    }
    pthread_mutex_unlock(&gVirtualFilesLock);
}

// ===[ funopen cookie backed by an AAsset ]===

static int assetRead(void* cookie, char* buf, int size) {
    return AAsset_read(((VirtualFile*) cookie)->asset, buf, (size_t) size);
}

static fpos64_t assetSeek(void* cookie, fpos64_t offset, int whence) {
    return (fpos64_t) AAsset_seek64(((VirtualFile*) cookie)->asset, (off64_t) offset, whence);
}

static int assetClose(void* cookie) {
    VirtualFile* vf = (VirtualFile*) cookie;
    unregisterVirtualFile(vf);
    AAsset_close(vf->asset);
    free(vf);
    return 0;
}

static FILE* openVirtual(const char* assetPath, const char* mode) {
    if (strchr(mode, 'w') != NULL || strchr(mode, 'a') != NULL || strchr(mode, '+') != NULL) {
        errno = EROFS;
        return NULL;
    }
    AAsset* asset = AAssetManager_open(gAssetManager, assetPath, AASSET_MODE_RANDOM);
    if (asset == NULL) {
        errno = ENOENT;
        return NULL;
    }
    VirtualFile* vf = calloc(1, sizeof(VirtualFile));
    if (vf == NULL) {
        AAsset_close(asset);
        errno = ENOMEM;
        return NULL;
    }
    vf->asset = asset;
    FILE* f = funopen64(vf, assetRead, NULL, assetSeek, assetClose);
    if (f == NULL) {
        AAsset_close(asset);
        free(vf);
        return NULL;
    }
    vf->file = f;
    registerVirtualFile(vf);
    return f;
}

FILE* __wrap_fopen(const char* path, const char* mode) {
    char buf[PATH_MAX];
    const char* assetPath = toAssetPath(path, buf);
    if (assetPath != NULL) return openVirtual(assetPath, mode);
    return __real_fopen(path, mode);
}

FILE* __wrap_fopen64(const char* path, const char* mode) {
    char buf[PATH_MAX];
    const char* assetPath = toAssetPath(path, buf);
    if (assetPath != NULL) return openVirtual(assetPath, mode);
    return __real_fopen64(path, mode);
}

int __wrap_stat(const char* path, struct stat* st) {
    char buf[PATH_MAX];
    const char* assetPath = toAssetPath(path, buf);
    if (assetPath == NULL) return __real_stat(path, st);

    memset(st, 0, sizeof(*st));
    AAsset* asset = AAssetManager_open(gAssetManager, assetPath, AASSET_MODE_UNKNOWN);
    if (asset != NULL) {
        st->st_mode = S_IFREG | 0444;
        st->st_size = AAsset_getLength64(asset);
        AAsset_close(asset);
        return 0;
    }

    // Not a file; treat it as a directory if it has at least one file directly in it
    // (AAssetManager can't tell an empty/missing directory apart, and its listing skips subdirectories)
    AAssetDir* dir = AAssetManager_openDir(gAssetManager, assetPath);
    bool isDir = dir != NULL && AAssetDir_getNextFileName(dir) != NULL;
    if (dir != NULL) AAssetDir_close(dir);
    if (isDir) {
        st->st_mode = S_IFDIR | 0555;
        return 0;
    }

    errno = ENOENT;
    return -1;
}

int __wrap_fileno(FILE* f) {
    pthread_mutex_lock(&gVirtualFilesLock);
    for (int i = 0; MAX_VIRTUAL_FILES > i; i++) {
        if (gVirtualFiles[i] != NULL && gVirtualFiles[i]->file == f) {
            pthread_mutex_unlock(&gVirtualFilesLock);
            return FAKE_FD_BASE + i;
        }
    }
    pthread_mutex_unlock(&gVirtualFilesLock);
    return __real_fileno(f);
}

int __wrap_fstat(int fd, struct stat* st) {
    if (FAKE_FD_BASE > fd || fd >= FAKE_FD_BASE + MAX_VIRTUAL_FILES) return __real_fstat(fd, st);

    pthread_mutex_lock(&gVirtualFilesLock);
    VirtualFile* vf = gVirtualFiles[fd - FAKE_FD_BASE];
    if (vf == NULL) {
        pthread_mutex_unlock(&gVirtualFilesLock);
        errno = EBADF;
        return -1;
    }
    memset(st, 0, sizeof(*st));
    st->st_mode = S_IFREG | 0444;
    st->st_size = AAsset_getLength64(vf->asset);
    pthread_mutex_unlock(&gVirtualFilesLock);
    return 0;
}

