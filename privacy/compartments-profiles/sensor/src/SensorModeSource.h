/*
 * FreeholdOS (ADR-208): where the sensor service gets its SensorModeTable from.
 *
 * Why this diverges from upstream: the policy lives in the system server and the sensor service is a separate process, so the
 * system server writes the table to a file that only it may write and this class reads it. A file that is missing, too large,
 * owned by anyone but the required owner or writable by group or others is not read. Before a first table was read every app
 * is on the folded events of ADR-167; after it, a file that cannot be used leaves the table that was read last (falling back
 * to the folded events would hand an app that is substituted or blocked its real motion data until the file is usable again).
 * The file is looked at once per second at most, so the event path costs a lock and a clock read.
 */
#ifndef ANDROID_SENSOR_MODE_SOURCE_H
#define ANDROID_SENSOR_MODE_SOURCE_H

#include "SensorModes.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <chrono>
#include <functional>
#include <memory>
#include <mutex>
#include <string>

namespace android {

class SensorModeSource {
public:
    using Clock = std::function<std::chrono::steady_clock::time_point()>;

    // requiredOwner is AID_SYSTEM in the service; the test passes its own uid.
    SensorModeSource(std::string path, uid_t requiredOwner,
                     Clock clock = [] { return std::chrono::steady_clock::now(); })
        : mPath(std::move(path)), mOwner(requiredOwner), mClock(std::move(clock)),
          mTable(std::make_shared<const SensorModeTable>()) {}

    // The current table. Never null; the status quo (every app folded) until a file has been read.
    std::shared_ptr<const SensorModeTable> table() {
        std::lock_guard<std::mutex> lock(mLock);
        const auto now = mClock();
        if (now >= mNextCheck) {
            mNextCheck = now + std::chrono::seconds(1);
            reloadLocked();
        }
        return mTable;
    }

private:
    static constexpr off_t kMaxFileBytes = 256 * 1024;

    void reloadLocked() {
        const int fd = open(mPath.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
        struct stat st;
        if (fd < 0 || fstat(fd, &st) != 0 || !S_ISREG(st.st_mode) || st.st_uid != mOwner ||
            (st.st_mode & (S_IWGRP | S_IWOTH)) != 0 || st.st_size > kMaxFileBytes) {
            if (fd >= 0) close(fd);
            return;  // the table of the last good read stays
        }
        if (mHaveSignature && st.st_mtim.tv_sec == mSec && st.st_mtim.tv_nsec == mNsec && st.st_size == mSize) {
            close(fd);
            return;
        }
        std::string text(static_cast<size_t>(st.st_size), '\0');
        const ssize_t got = st.st_size == 0 ? 0 : pread(fd, &text[0], text.size(), 0);
        close(fd);
        auto next = std::make_shared<SensorModeTable>();
        if (got != st.st_size || !next->parse(text)) {
            return;
        }
        mHaveSignature = true;
        mSec = st.st_mtim.tv_sec;
        mNsec = st.st_mtim.tv_nsec;
        mSize = st.st_size;
        mTable = std::move(next);
    }

    const std::string mPath;
    const uid_t mOwner;
    const Clock mClock;
    std::mutex mLock;
    std::chrono::steady_clock::time_point mNextCheck{};
    bool mHaveSignature = false;
    time_t mSec = 0;
    long mNsec = 0;
    off_t mSize = 0;
    std::shared_ptr<const SensorModeTable> mTable;
};

}  // namespace android

#endif  // ANDROID_SENSOR_MODE_SOURCE_H
