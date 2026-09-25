Í
M
TestDcClientTestcom.telegramyou.app$signsInAndSendsAPhotoToSavedMessagesš
Ùjava.lang.IllegalStateException: No test data centre let us in:
+9996621762, 5 digits (We've sent an SMS with the code to +9996621762): WaitCode PHONE_CODE_INVALID
	at com.telegramyou.app.TestDcClientTest$signsInAndSendsAPhotoToSavedMessages$1.invokeSuspend(TestDcClientTest.kt:118)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:98)
	at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:263)
	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:94)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:70)
	at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source:1)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking$default(Builders.kt:48)
	at kotlinx.coroutines.BuildersKt.runBlocking$default(Unknown Source:1)
	at com.telegramyou.app.TestDcClientTest.signsInAndSendsAPhotoToSavedMessages(TestDcClientTest.kt:66)
java.lang.RuntimeExceptionŸjava.lang.RuntimeException: java.lang.IllegalStateException: No test data centre let us in:
+9996621762, 5 digits (We've sent an SMS with the code to +9996621762): WaitCode PHONE_CODE_INVALID
	at com.telegramyou.app.TestDcClientTest$signsInAndSendsAPhotoToSavedMessages$1.invokeSuspend(TestDcClientTest.kt:118)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:98)
	at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:263)
	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:94)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:70)
	at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source:1)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking$default(Builders.kt:48)
	at kotlinx.coroutines.BuildersKt.runBlocking$default(Unknown Source:1)
	at com.telegramyou.app.TestDcClientTest.signsInAndSendsAPhotoToSavedMessages(TestDcClientTest.kt:66)

	at com.android.tools.androidtest.testengine.descriptor.AndroidDynamicTestDescriptor.execute(AndroidDynamicTestDescriptor.kt:68)
	at com.android.tools.androidtest.testengine.descriptor.AndroidDynamicTestDescriptor.execute(AndroidDynamicTestDescriptor.kt:36)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:373)
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1182)
	at java.base/java.util.concurrent.ForkJoinPool.scan(ForkJoinPool.java:1655)
	at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1622)
	at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:165)
"Ü

logcatandroidÆ
Ã/home/runner/work/TelegramYou/TelegramYou/app/build/outputs/androidTest-results/connected/debug/test(AVD) - 14/logcat-com.telegramyou.app.TestDcClientTest-signsInAndSendsAPhotoToSavedMessages.txt*™

device-infoandroid
}/home/runner/work/TelegramYou/TelegramYou/app/build/outputs/androidTest-results/connected/debug/test(AVD) - 14/device-info.pb