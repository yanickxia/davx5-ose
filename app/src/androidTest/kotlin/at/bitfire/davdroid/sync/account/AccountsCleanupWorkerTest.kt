package at.bitfire.davdroid.sync.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalAddressBook.Companion.USER_DATA_COLLECTION_ID
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AccountsCleanupWorkerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var accountsCleanupWorkerFactory: AccountsCleanupWorker.Factory

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    lateinit var accountManager: AccountManager
    lateinit var addressBookAccountType: String
    lateinit var addressBookAccount: Account
    lateinit var service: Service

    @Before
    fun setUp() {
        hiltRule.inject()

        service = createTestService(Service.TYPE_CARDDAV)

        // Prepare test account
        accountManager = AccountManager.get(context)
        addressBookAccountType = context.getString(R.string.account_type_address_book)
        addressBookAccount = Account(
            "Fancy address book account",
            addressBookAccountType
        )

        // Initialize WorkManager for instrumentation tests.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setWorkerFactory(workerFactory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After
    fun tearDown() {
        // Remove the account here in any case; Nice to have when the test fails
        accountManager.removeAccountExplicitly(addressBookAccount)
    }


    @Test
    fun testDeleteOrphanedAddressBookAccounts_deletesAddressBookAccountWithoutCollection() {
        // Create address book account without corresponding collection
        assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, null))

        val addressBookAccounts = accountManager.getAccountsByType(addressBookAccountType)
        assertEquals(addressBookAccount, addressBookAccounts.firstOrNull())

        // Create worker and run the method
        val worker = TestListenableWorkerBuilder<AccountsCleanupWorker>(context)
            .setWorkerFactory(object: WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    accountsCleanupWorkerFactory.create(appContext, workerParameters)
            })
            .build()
        worker.deleteOrphanedAddressBookAccounts(addressBookAccounts)

        // Verify account was deleted
        assertTrue(accountManager.getAccountsByType(addressBookAccountType).isEmpty())
    }

    @Test
    fun testDeleteOrphanedAddressBookAccounts_leavesAddressBookAccountWithCollection() {
        // Create address book account _with_ corresponding collection and verify
        val randomCollectionId = 12345L
        val userData = Bundle(1).apply {
            putString(USER_DATA_COLLECTION_ID, "$randomCollectionId")
        }
        assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, userData))

        val addressBookAccounts = accountManager.getAccountsByType(addressBookAccountType)
        assertEquals(randomCollectionId, accountManager.getUserData(addressBookAccount, USER_DATA_COLLECTION_ID).toLong())

        // Create the collection
        val collectionDao = db.collectionDao()
        collectionDao.insert(Collection(
            randomCollectionId,
            serviceId = service.id,
            type = Collection.TYPE_ADDRESSBOOK,
            url = "http://www.example.com/yay.php".toHttpUrl()
        ))

        // Create worker and run the method
        val worker = TestListenableWorkerBuilder<AccountsCleanupWorker>(context)
            .setWorkerFactory(object: WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    accountsCleanupWorkerFactory.create(appContext, workerParameters)
            })
            .build()
        worker.deleteOrphanedAddressBookAccounts(addressBookAccounts)

        // Verify account was _not_ deleted
        assertEquals(addressBookAccount, addressBookAccounts.firstOrNull())
    }


    // helpers

    private fun createTestService(serviceType: String): Service {
        val service = Service(id=0, accountName="test", type=serviceType, principal = null)
        val serviceId = db.serviceDao().insertOrReplace(service)
        return db.serviceDao().get(serviceId)!!
    }

}