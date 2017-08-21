/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.server.pm;

import android.content.Context;
import android.content.pm.UserInfo;
import android.os.FileUtils;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.platform.test.annotations.Presubmit;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <p>Run with:<pre>
 * m FrameworksServicesTests &&
 * adb install \
 * -r out/target/product/hammerhead/data/app/FrameworksServicesTests/FrameworksServicesTests.apk &&
 * adb shell am instrument -e class com.android.server.pm.UserDataPreparerTest \
 * -w com.android.frameworks.servicestests/android.support.test.runner.AndroidJUnitRunner
 * </pre>
 */
@RunWith(AndroidJUnit4.class)
@Presubmit
@SmallTest
public class UserDataPreparerTest {

    private static final int TEST_USER_SERIAL = 1000;
    private static final int TEST_USER_ID = 10;

    private TestUserDataPreparer mUserDataPreparer;

    @Mock
    private StorageManager mStorageManagerMock;

    @Mock
    private Context mContextMock;

    @Mock
    private Installer mInstaller;

    private Object mInstallLock;

    @Before
    public void setup() {
        Context ctx = InstrumentationRegistry.getContext();
        FileUtils.deleteContents(ctx.getCacheDir());
        mInstallLock = new Object();
        MockitoAnnotations.initMocks(this);
        mUserDataPreparer = new TestUserDataPreparer(mInstaller, mInstallLock, mContextMock, false,
                ctx.getCacheDir());
        when(mContextMock.getSystemServiceName(StorageManager.class))
                .thenReturn(Context.STORAGE_SERVICE);
        when(mContextMock.getSystemService(eq(Context.STORAGE_SERVICE)))
                .thenReturn(mStorageManagerMock);
        VolumeInfo testVolume = new VolumeInfo("testuuid", VolumeInfo.TYPE_PRIVATE, null, null);
        when(mStorageManagerMock.getWritablePrivateVolumes()).thenReturn(Arrays.asList(testVolume));
    }

    @Test
    public void testPrepareUserData_De() throws Exception {
        File userDeDir = mUserDataPreparer.getDataUserDeDirectory(null, TEST_USER_ID);
        userDeDir.mkdirs();
        File systemDeDir = mUserDataPreparer.getDataSystemDeDirectory(TEST_USER_ID);
        systemDeDir.mkdirs();
        mUserDataPreparer
                .prepareUserData(TEST_USER_ID, TEST_USER_SERIAL, StorageManager.FLAG_STORAGE_DE);
        verify(mStorageManagerMock).prepareUserStorage(isNull(String.class), eq(TEST_USER_ID),
                eq(TEST_USER_SERIAL), eq(StorageManager.FLAG_STORAGE_DE));
        verify(mInstaller).createUserData(isNull(String.class), eq(TEST_USER_ID),
                eq(TEST_USER_SERIAL), eq(StorageManager.FLAG_STORAGE_DE));
        int serialNumber = UserDataPreparer.getSerialNumber(userDeDir);
        assertEquals(TEST_USER_SERIAL, serialNumber);
        serialNumber = UserDataPreparer.getSerialNumber(systemDeDir);
        assertEquals(TEST_USER_SERIAL, serialNumber);
    }

    @Test
    public void testPrepareUserData_Ce() throws Exception {
        File userCeDir = mUserDataPreparer.getDataUserCeDirectory(null, TEST_USER_ID);
        userCeDir.mkdirs();
        File systemCeDir = mUserDataPreparer.getDataSystemCeDirectory(TEST_USER_ID);
        systemCeDir.mkdirs();
        mUserDataPreparer
                .prepareUserData(TEST_USER_ID, TEST_USER_SERIAL, StorageManager.FLAG_STORAGE_CE);
        verify(mStorageManagerMock).prepareUserStorage(isNull(String.class), eq(TEST_USER_ID),
                eq(TEST_USER_SERIAL), eq(StorageManager.FLAG_STORAGE_CE));
        verify(mInstaller).createUserData(isNull(String.class), eq(TEST_USER_ID),
                eq(TEST_USER_SERIAL), eq(StorageManager.FLAG_STORAGE_CE));
        int serialNumber = UserDataPreparer.getSerialNumber(userCeDir);
        assertEquals(TEST_USER_SERIAL, serialNumber);
        serialNumber = UserDataPreparer.getSerialNumber(systemCeDir);
        assertEquals(TEST_USER_SERIAL, serialNumber);
    }

    @Test
    public void testDestroyUserData() throws Exception {
        // Add file in CE
        File systemCeDir = mUserDataPreparer.getDataSystemCeDirectory(TEST_USER_ID);
        systemCeDir.mkdirs();
        File ceFile = new File(systemCeDir, "file");
        writeFile(ceFile, "-----" );
        testDestroyUserData_De();
        // CE directory should be preserved
        assertEquals(Collections.singletonList(ceFile), Arrays.asList(FileUtils.listFilesOrEmpty(
                systemCeDir)));

        testDestroyUserData_Ce();

        // Verify that testDir is empty
        assertEquals(Collections.emptyList(), Arrays.asList(FileUtils.listFilesOrEmpty(
                mUserDataPreparer.testDir)));
    }

    @Test
    public void testDestroyUserData_De() throws Exception {
        File systemDir = mUserDataPreparer.getUserSystemDirectory(TEST_USER_ID);
        systemDir.mkdirs();
        writeFile(new File(systemDir, "file"), "-----" );
        File systemDeDir = mUserDataPreparer.getDataSystemDeDirectory(TEST_USER_ID);
        systemDeDir.mkdirs();
        writeFile(new File(systemDeDir, "file"), "-----" );
        File miscDeDir = mUserDataPreparer.getDataMiscDeDirectory(TEST_USER_ID);
        miscDeDir.mkdirs();
        writeFile(new File(miscDeDir, "file"), "-----" );

        mUserDataPreparer.destroyUserData(TEST_USER_ID, StorageManager.FLAG_STORAGE_DE);

        verify(mInstaller).destroyUserData(isNull(String.class), eq(TEST_USER_ID),
                        eq(StorageManager.FLAG_STORAGE_DE));
        verify(mStorageManagerMock).destroyUserStorage(isNull(String.class), eq(TEST_USER_ID),
                        eq(StorageManager.FLAG_STORAGE_DE));

        assertEquals(Collections.emptyList(), Arrays.asList(FileUtils.listFilesOrEmpty(systemDir)));
        assertEquals(Collections.emptyList(), Arrays.asList(FileUtils.listFilesOrEmpty(
                systemDeDir)));
        assertEquals(Collections.emptyList(), Arrays.asList(FileUtils.listFilesOrEmpty(
                miscDeDir)));
    }

    @Test
    public void testDestroyUserData_Ce() throws Exception {
        File systemCeDir = mUserDataPreparer.getDataSystemCeDirectory(TEST_USER_ID);
        systemCeDir.mkdirs();
        writeFile(new File(systemCeDir, "file"), "-----" );
        File miscCeDir = mUserDataPreparer.getDataMiscCeDirectory(TEST_USER_ID);
        miscCeDir.mkdirs();
        writeFile(new File(miscCeDir, "file"), "-----" );

        mUserDataPreparer.destroyUserData(TEST_USER_ID, StorageManager.FLAG_STORAGE_CE);

        verify(mInstaller).destroyUserData(isNull(String.class), eq(TEST_USER_ID),
                eq(StorageManager.FLAG_STORAGE_CE));
        verify(mStorageManagerMock).destroyUserStorage(isNull(String.class), eq(TEST_USER_ID),
                eq(StorageManager.FLAG_STORAGE_CE));

        assertEquals(Collections.emptyList(), Arrays.asList(FileUtils.listFilesOrEmpty(
                systemCeDir)));
        assertEquals(Collections.emptyList(), Arrays.asList(FileUtils.listFilesOrEmpty(
                miscCeDir)));
    }

    @Test
    public void testReconcileUsers() throws Exception {
        UserInfo u1 = new UserInfo(1, "u1", 0);
        UserInfo u2 = new UserInfo(2, "u2", 0);
        File testDir = mUserDataPreparer.testDir;
        File dir1 = new File(testDir, "1");
        dir1.mkdirs();
        File dir2 = new File(testDir, "2");
        dir2.mkdirs();
        File dir3 = new File(testDir, "3");
        dir3.mkdirs();

        mUserDataPreparer
                .reconcileUsers(StorageManager.UUID_PRIVATE_INTERNAL, Arrays.asList(u1, u2),
                        Arrays.asList(dir1, dir2, dir3));
        // Verify that user 3 data is removed
        verify(mInstaller).destroyUserData(isNull(String.class), eq(3),
                eq(StorageManager.FLAG_STORAGE_DE|StorageManager.FLAG_STORAGE_CE));
    }

    private static void writeFile(File file, String content) throws IOException {
        try (FileOutputStream os = new FileOutputStream(file)) {
            os.write(content.getBytes(Charset.defaultCharset()));
        }
    }

    private static class TestUserDataPreparer extends UserDataPreparer {
        File testDir;

        TestUserDataPreparer(Installer installer, Object installLock, Context context,
                boolean onlyCore, File testDir) {
            super(installer, installLock, context, onlyCore);
            this.testDir = testDir;
        }

        @Override
        protected File getDataMiscCeDirectory(int userId) {
            return new File(testDir, "misc_ce_" + userId);
        }

        @Override
        protected File getDataSystemCeDirectory(int userId) {
            return new File(testDir, "system_ce_" + userId);
        }

        @Override
        protected File getDataMiscDeDirectory(int userId) {
            return new File(testDir, "misc_de_" + userId);
        }

        @Override
        protected File getUserSystemDirectory(int userId) {
            return new File(testDir, "user_system_" + userId);
        }

        @Override
        protected File getDataUserCeDirectory(String volumeUuid, int userId) {
            return new File(testDir, "user_ce_" + userId);
        }

        @Override
        protected File getDataSystemDeDirectory(int userId) {
            return new File(testDir, "system_de_" + userId);
        }

        @Override
        protected File getDataUserDeDirectory(String volumeUuid, int userId) {
            return new File(testDir, "user_de_" + userId);
        }

        @Override
        protected boolean isFileEncryptedEmulatedOnly() {
            return false;
        }
    }

}
