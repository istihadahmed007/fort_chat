package com.fort.messenger

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import com.fort.messenger.data.local.FortDatabase
import com.fort.messenger.data.local.KnockFirstRequestEntity
import com.fort.messenger.data.remote.InMemoryRemoteRelay
import com.fort.messenger.data.repository.FortRepository
import com.fort.messenger.model.CardType
import com.fort.messenger.model.PassDurationType
import com.fort.messenger.security.KeyStoreMaster
import com.fort.messenger.ui.screens.auth.AuthScreen
import com.fort.messenger.ui.screens.chat.ConversationScreen
import com.fort.messenger.ui.screens.chats.ChatsHomeScreen
import com.fort.messenger.ui.screens.requests.KnockFirstRequestsScreen
import com.fort.messenger.ui.theme.FortTheme
import com.fort.messenger.viewmodel.FortMainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignScreenshotCaptureTest {

    private lateinit var application: Application
    private lateinit var database: FortDatabase
    private lateinit var relay: InMemoryRemoteRelay
    private lateinit var keyStoreMaster: KeyStoreMaster
    private lateinit var repository: FortRepository
    private lateinit var viewModel: FortMainViewModel
    private val artifactDir = File("C:/Users/Tonmoy/.gemini/antigravity-ide/brain/c9f29d1a-b528-4cc6-a853-095f11890de1")

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        database = FortDatabase.createInMemory(application)
        relay = InMemoryRemoteRelay()
        keyStoreMaster = KeyStoreMaster()
        repository = FortRepository(database, relay, keyStoreMaster)
        viewModel = FortMainViewModel(application, repository)
    }

    @After
    fun tearDown() {
        database.close()
        relay.clearAllData()
    }

    private fun captureView(fileName: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity).apply {
            setContent(content)
        }
        activity.setContentView(composeView)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val width = 1080
        val height = 2340
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        composeView.draw(canvas)

        artifactDir.mkdirs()
        val file = File(artifactDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        println("Saved screenshot to: ${file.absolutePath} (${file.length()} bytes)")
    }

    @Test
    fun testCaptureChatsScreenLightAndDark() = runBlocking {
        // Register an authenticated user so ChatsHomeScreen displays the home UI
        repository.register("user@fort.chat", "Password123!", "Sovereign User").getOrThrow()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        captureView("screen1_chats_light.png") {
            FortTheme(darkTheme = false) {
                ChatsHomeScreen(
                    viewModel = viewModel,
                    onNavigateToChat = {},
                    onNavigateToYou = {}
                )
            }
        }

        captureView("screen1_chats_dark.png") {
            FortTheme(darkTheme = true) {
                ChatsHomeScreen(
                    viewModel = viewModel,
                    onNavigateToChat = {},
                    onNavigateToYou = {}
                )
            }
        }
    }

    @Test
    fun testCaptureKnockFirstRequestsScreen() = runBlocking {
        val user = repository.register("user@fort.chat", "Password123!", "Sovereign User").getOrThrow()

        // Insert an inbound Knock First request matching Mobile Privacy Showcase Phone 2
        val req = KnockFirstRequestEntity(
            requestId = "req_101",
            recipientUserId = user.userId,
            senderUserId = "peer_202",
            senderDisplayName = "Maya Chen",
            senderCardType = CardType.PERSONAL,
            source = "QR Code Scan",
            rawMessage = "Hi! We met at the design summit. I'd love to stay in touch here.",
            sandboxedLink = null,
            timestamp = "2h ago",
            status = "PENDING"
        )
        database.knockFirstDao().insertRequest(req)
        repository.syncInboundKnockFirstRequests(user.userId)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        captureView("screen2_knock_first_light.png") {
            FortTheme(darkTheme = false) {
                KnockFirstRequestsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
    }

    @Test
    fun testCaptureConversationScreen() = runBlocking {
        val user = repository.register("user@fort.chat", "Password123!", "Sovereign User").getOrThrow()
        val peer = repository.register("peer@fort.chat", "Password456!", "Maya Chen").getOrThrow()

        // Establish connection pass
        val pass = repository.generatePass(peer.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow()
        repository.claimPass(pass.token, user.userId, "Sovereign User").getOrThrow()

        val convId = "conv_${peer.userId}"

        // Seed conversation messages matching Mobile Privacy Showcase Phone 3
        val m1 = com.fort.messenger.data.local.ChatMessageEntity(
            messageId = "msg_1",
            conversationId = convId,
            senderUserId = peer.userId,
            recipientUserId = user.userId,
            ciphertext = "mock_cipher_1",
            iv = "mock_iv_1",
            ephemeralKey = "mock_key_1",
            decryptedTextCache = "Hey! Great to finally connect here. This app feels so calm.",
            timestamp = 1700000001000L,
            deliveryStatus = "READ",
            isMine = false
        )
        val m2 = com.fort.messenger.data.local.ChatMessageEntity(
            messageId = "msg_2",
            conversationId = convId,
            senderUserId = user.userId,
            recipientUserId = peer.userId,
            ciphertext = "mock_cipher_2",
            iv = "mock_iv_2",
            ephemeralKey = "mock_key_2",
            decryptedTextCache = "Agreed! Love the focus on privacy and real conversations.",
            timestamp = 1700000002000L,
            deliveryStatus = "READ",
            isMine = true
        )
        val m3 = com.fort.messenger.data.local.ChatMessageEntity(
            messageId = "msg_3",
            conversationId = convId,
            senderUserId = peer.userId,
            recipientUserId = user.userId,
            ciphertext = "mock_cipher_3",
            iv = "mock_iv_3",
            ephemeralKey = "mock_key_3",
            decryptedTextCache = "Same. Are you free to hop on a quick call later this week?",
            timestamp = 1700000003000L,
            deliveryStatus = "READ",
            isMine = false
        )
        val m4 = com.fort.messenger.data.local.ChatMessageEntity(
            messageId = "msg_4",
            conversationId = convId,
            senderUserId = user.userId,
            recipientUserId = peer.userId,
            ciphertext = "mock_cipher_4",
            iv = "mock_iv_4",
            ephemeralKey = "mock_key_4",
            decryptedTextCache = "Definitely! How about Thursday evening?",
            timestamp = 1700000004000L,
            deliveryStatus = "READ",
            isMine = true
        )
        val m5 = com.fort.messenger.data.local.ChatMessageEntity(
            messageId = "msg_5",
            conversationId = convId,
            senderUserId = peer.userId,
            recipientUserId = user.userId,
            ciphertext = "mock_cipher_5",
            iv = "mock_iv_5",
            ephemeralKey = "mock_key_5",
            decryptedTextCache = "Perfect. I'll send a time.",
            timestamp = 1700000005000L,
            deliveryStatus = "READ",
            isMine = false,
            reactionsJson = "{\"👍\":[\"${user.userId}\"]}"
        )

        database.chatMessageDao().insertMessage(m1)
        database.chatMessageDao().insertMessage(m2)
        database.chatMessageDao().insertMessage(m3)
        database.chatMessageDao().insertMessage(m4)
        database.chatMessageDao().insertMessage(m5)

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        captureView("screen3_conversation_light.png") {
            FortTheme(darkTheme = false) {
                ConversationScreen(
                    conversationId = convId,
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
    }

    @Test
    fun testCaptureAuthScreen() {
        captureView("screen4_auth_light.png") {
            FortTheme(darkTheme = false) {
                AuthScreen(viewModel = viewModel)
            }
        }
    }
}
