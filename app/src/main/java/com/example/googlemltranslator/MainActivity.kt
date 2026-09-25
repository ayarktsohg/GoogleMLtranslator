package com.example.googlemltranslator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.googlemltranslator.ui.theme.GoogleMLtranslatorTheme
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.common.model.DownloadConditions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.PaddingValues
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import android.os.StatFs
import java.util.Locale
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            GoogleMLtranslatorTheme {
                TranslatorScreen()
            }
        }
    }
}

@Composable
fun TranslatorScreen() {

    var inputText by remember {
        mutableStateOf("")
    }

    var outputText by remember {
        mutableStateOf("")
    }

    var sourceLanguage by remember {
        mutableStateOf(TranslateLanguage.RUSSIAN)
    }

    var targetLanguage by remember {
        mutableStateOf(TranslateLanguage.ENGLISH)
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    val coroutineScope = rememberCoroutineScope()

    var autoTranslateJob by remember {
        mutableStateOf<Job?>(null)
    }

    val context =
        LocalContext.current

    val focusManager =
        LocalFocusManager.current

    var layoutEditMode by remember {
        mutableStateOf(false)
    }

    val layoutOrder =
        remember {
            mutableStateListOf<LayoutBlock>()
                .apply {
                    addAll(
                        loadLayoutOrder(
                            context
                        )
                    )
                }
        }

    val controlOrder =
        remember {
            mutableStateListOf<ControlItem>()
                .apply {
                    addAll(
                        loadControlOrder(
                            context
                        )
                    )
                }
        }

    val inputOrder =
        remember {

            mutableStateListOf<InputItem>()
                .apply {

                    addAll(
                        loadInputOrder(
                            context
                        )
                    )
                }
        }

    fun moveInputItem(
        item: InputItem,
        direction: Int
    ) {

        val currentIndex =
            inputOrder.indexOf(item)

        if (currentIndex == -1) {
            return
        }

        val newIndex =
            currentIndex + direction

        if (
            newIndex !in
            inputOrder.indices
        ) {
            return
        }

        inputOrder.removeAt(
            currentIndex
        )

        inputOrder.add(
            newIndex,
            item
        )

        saveInputOrder(
            context,
            inputOrder
        )
    }

    fun moveControlItem(
        item: ControlItem,
        direction: Int
    ) {

        val currentIndex =
            controlOrder.indexOf(item)

        if (currentIndex == -1) {
            return
        }

        val newIndex =
            currentIndex + direction

        if (
            newIndex !in
            controlOrder.indices
        ) {
            return
        }

        controlOrder.removeAt(
            currentIndex
        )

        controlOrder.add(
            newIndex,
            item
        )

        saveControlOrder(
            context,
            controlOrder
        )
    }

    fun moveLayoutBlock(
        block: LayoutBlock,
        direction: Int
    ) {

        val currentIndex =
            layoutOrder.indexOf(block)

        if (currentIndex == -1) {
            return
        }

        val newIndex =
            currentIndex + direction

        if (
            newIndex !in
            layoutOrder.indices
        ) {
            return
        }

        layoutOrder.removeAt(
            currentIndex
        )

        layoutOrder.add(
            newIndex,
            block
        )

        saveLayoutOrder(
            context,
            layoutOrder
        )
    }

    var translationRequestId by remember {
        mutableIntStateOf(0)
    }

    val options =
        remember(
            sourceLanguage,
            targetLanguage
        ) {

            TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()
        }

    val translator = remember(options) {
        Translation.getClient(options)
    }

    val modelManager = remember {
        RemoteModelManager.getInstance()
    }

    var downloadedLanguages by remember {
        mutableStateOf(
            setOf(
                TranslateLanguage.ENGLISH
            )
        )
    }

    var pendingInstallLanguage by remember {
        mutableStateOf<AppLanguage?>(null)
    }

    var insufficientSpaceLanguage by remember {
        mutableStateOf<AppLanguage?>(null)
    }

    var installingLanguage by remember {
        mutableStateOf<AppLanguage?>(null)
    }

    var installTimeoutJob by remember {
        mutableStateOf<Job?>(null)
    }

    var installErrorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var freeStorageForDialog by remember {
        mutableLongStateOf(0L)
    }

    val supportedLanguages =
        remember {
            getSupportedLanguages()
        }

    fun languageName(
        languageCode: String
    ): String {

        return supportedLanguages
            .firstOrNull {
                it.code == languageCode
            }
            ?.name
            ?: languageCode
    }

    LaunchedEffect(Unit) {

        modelManager
            .getDownloadedModels(
                TranslateRemoteModel::class.java
            )
            .addOnSuccessListener { models ->

                downloadedLanguages =
                    models
                        .map {
                            it.language
                        }
                        .toSet() +
                            TranslateLanguage.ENGLISH
            }
    }

    fun ensureLanguageModel(
        languageCode: String,
        allowDownload: Boolean,
        showStatus: Boolean,
        onReady: () -> Unit,
        onFailure: (String) -> Unit
    ) {

        /*
         * Английская модель встроена в ML Kit.
         */
        if (
            languageCode ==
            TranslateLanguage.ENGLISH
        ) {

            onReady()
            return
        }


        /*
         * Уже знаем, что модель есть.
         */
        if (
            languageCode in
            downloadedLanguages
        ) {

            onReady()
            return
        }


        val model =
            TranslateRemoteModel
                .Builder(languageCode)
                .build()


        if (showStatus) {

            outputText =
                "Проверка модели: " +
                        languageName(languageCode)
        }


        modelManager
            .isModelDownloaded(model)
            .addOnSuccessListener { downloaded ->

                if (downloaded) {

                    downloadedLanguages =
                        downloadedLanguages +
                                languageCode

                    onReady()

                    return@addOnSuccessListener
                }


                if (!allowDownload) {

                    onFailure(
                        "Модель языка «" +
                                languageName(languageCode) +
                                "» не установлена"
                    )

                    return@addOnSuccessListener
                }


                if (showStatus) {

                    outputText =
                        "Загрузка модели: " +
                                languageName(languageCode)
                }


                val conditions =
                    DownloadConditions
                        .Builder()
                        .build()



                modelManager
                    .download(
                        model,
                        conditions
                    )
                    .addOnSuccessListener {

                        downloadedLanguages =
                            downloadedLanguages +
                                    languageCode

                        onReady()
                    }
                    .addOnFailureListener { exception ->

                        onFailure(
                            "Ошибка загрузки модели «" +
                                    languageName(languageCode) +
                                    "»: " +
                                    exception.message
                        )
                    }
            }
            .addOnFailureListener { exception ->

                onFailure(
                    "Ошибка проверки модели «" +
                            languageName(languageCode) +
                            "»: " +
                            exception.message
                )
            }
    }

    fun requestTranslation(
        text: String,
        allowModelDownload: Boolean,
        showStatus: Boolean
    ) {

        if (text.isBlank()) {

            if (showStatus) {
                outputText = ""
                isLoading = false
            }

            return
        }


        translationRequestId += 1

        val requestId =
            translationRequestId


        fun fail(
            message: String
        ) {

            if (
                requestId !=
                translationRequestId
            ) {
                return
            }

            if (showStatus) {
                outputText = message
            }

            isLoading = false
        }


        fun performTranslation() {

            if (
                requestId !=
                translationRequestId
            ) {
                return
            }


            if (showStatus) {
                outputText =
                    "Перевод..."
            }


            translator
                .translate(text)
                .addOnSuccessListener {
                        translatedText ->

                    if (
                        requestId ==
                        translationRequestId
                    ) {

                        /*
                         * Только здесь появляется
                         * законченный результат.
                         */
                        outputText =
                            translatedText

                        isLoading = false
                    }
                }
                .addOnFailureListener {
                        exception ->

                    fail(
                        "Ошибка перевода: " +
                                exception.message
                    )
                }
        }


        /*
         * Сначала гарантируем наличие
         * модели исходного языка.
         */
        ensureLanguageModel(
            languageCode =
            sourceLanguage,

            allowDownload =
            allowModelDownload,

            showStatus =
            showStatus,

            onReady = {

                if (
                    requestId !=
                    translationRequestId
                ) {
                    return@ensureLanguageModel
                }


                /*
                 * Затем модели целевого языка.
                 */
                ensureLanguageModel(
                    languageCode =
                    targetLanguage,

                    allowDownload =
                    allowModelDownload,

                    showStatus =
                    showStatus,

                    onReady = {

                        performTranslation()
                    },

                    onFailure = {
                            message ->

                        fail(message)
                    }
                )
            },

            onFailure = {
                    message ->

                fail(message)
            }
        )
    }

    val density = LocalDensity.current

    val keyboardVisible =
        WindowInsets.ime.getBottom(density) > 0

    DisposableEffect(translator) {
        onDispose {
            translator.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(
                if (keyboardVisible) 8.dp
                else 12.dp
            )
    ) {

        val targetLanguageName =
            supportedLanguages
                .firstOrNull {
                    it.code == targetLanguage
                }
                ?.name
                ?: targetLanguage

        // ================================================================

        layoutOrder.forEachIndexed { index, block ->

            if (index > 0) {

                Spacer(
                    modifier = Modifier.height(10.dp)
                )
            }

            when (block) {

                LayoutBlock.RESULT -> {

                    EditableLayoutBlock(
                        block = LayoutBlock.RESULT,

                        editMode = layoutEditMode,

                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),

                        onMove = { direction ->

                            moveLayoutBlock(
                                LayoutBlock.RESULT,
                                direction
                            )
                        }
                    ) {

                        ResultPanel(
                            outputText = outputText,

                            targetLanguageName =
                            targetLanguageName,

                            modifier =
                            Modifier.fillMaxSize()
                        )
                    }
                }

                LayoutBlock.CONTROL -> {

                    EditableLayoutBlock(
                        block = LayoutBlock.CONTROL,

                        editMode = layoutEditMode,

                        modifier =
                        Modifier.fillMaxWidth(),

                        onMove = { direction ->

                            moveLayoutBlock(
                                LayoutBlock.CONTROL,
                                direction
                            )
                        }
                    ) {

                        ControlPanel(
                            sourceLanguage =
                            sourceLanguage,

                            targetLanguage =
                            targetLanguage,

                            languages =
                            supportedLanguages,

                            downloadedLanguages =
                            downloadedLanguages,

                            installingLanguageCode =
                            installingLanguage?.code,

                            layoutEditMode = layoutEditMode,

                            controlOrder =
                            controlOrder,

                            onMoveControlItem = {
                                    item,
                                    direction ->

                                moveControlItem(
                                    item,
                                    direction
                                )
                            },

                            onToggleLayoutEditMode = {

                                focusManager.clearFocus()

                                layoutEditMode =
                                    !layoutEditMode
                            },

                            onSwapLanguage = {

                                autoTranslateJob?.cancel()

                                translationRequestId += 1

                                val oldSource =
                                    sourceLanguage

                                sourceLanguage =
                                    targetLanguage

                                targetLanguage =
                                    oldSource


                                val oldInput =
                                    inputText

                                inputText =
                                    outputText

                                outputText =
                                    oldInput
                            },

                            onSelectTargetLanguage = { languageCode ->

                                autoTranslateJob?.cancel()
                                translationRequestId += 1

                                when {

                                    /*
                                     * Выбран исходный язык.
                                     *
                                     * Например:
                                     * RU → EN
                                     *
                                     * пользователь выбирает RU
                                     *
                                     * результат:
                                     * EN → RU
                                     */
                                    languageCode == sourceLanguage -> {

                                        val oldSource =
                                            sourceLanguage

                                        sourceLanguage =
                                            targetLanguage

                                        targetLanguage =
                                            oldSource


                                        val oldInput =
                                            inputText

                                        inputText =
                                            outputText

                                        outputText =
                                            oldInput
                                    }


                                    /*
                                     * Выбран уже установленный
                                     * целевой язык.
                                     *
                                     * Ничего менять не нужно.
                                     */
                                    languageCode == targetLanguage -> {
                                        // Ничего не делаем
                                    }


                                    /*
                                     * Выбран новый целевой язык.
                                     */
                                    else -> {

                                        targetLanguage =
                                            languageCode
                                    }
                                }
                            },

                            onRequestLanguageInstall = { language ->

                                val freeBytes =
                                    getFreeStorageBytes(context)

                                freeStorageForDialog =
                                    freeBytes

                                if (
                                    freeBytes <
                                    MIN_FREE_SPACE_FOR_MODEL_BYTES
                                ) {

                                    insufficientSpaceLanguage =
                                        language

                                } else {

                                    pendingInstallLanguage =
                                        language
                                }
                            }
                        )
                    }
                }

                LayoutBlock.INPUT -> {

                    EditableLayoutBlock(
                        block = LayoutBlock.INPUT,

                        editMode = layoutEditMode,

                        modifier =
                        Modifier.fillMaxWidth(),

                        onMove = { direction ->

                            moveLayoutBlock(
                                LayoutBlock.INPUT,
                                direction
                            )
                        }
                    ) {

                        InputPanel(
                            inputText = inputText,

                            layoutEditMode =
                            layoutEditMode,

                            inputOrder =
                            inputOrder,

                            onMoveInputItem = {
                                    item,
                                    direction ->

                                moveInputItem(
                                    item,
                                    direction
                                )
                            },

                            onInputTextChange = { newText ->
                                // твой существующий код
                            },

                            isLoading = isLoading,
                            compact = keyboardVisible,

                            onTranslate = {
                                // твой существующий код
                            }
                        )
                            // ===============================================================
                            // ===============================================================




                            // ===============================================================
                            // ===============================================================


                    }
                }
            }
        }





        if (layoutEditMode) {

            Text(
                text =
                "Режим расположения: " +
                        "удерживайте панель и " +
                        "перетаскивайте вверх или вниз",

                style =
                MaterialTheme.typography.labelMedium
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )
        }

    }
    pendingInstallLanguage?.let { language ->

        AlertDialog(
            onDismissRequest = {
                pendingInstallLanguage = null
            },

            title = {
                Text("Установка языкового пакета")
            },

            text = {

                Text(
                    "Вы уверены, что хотите установить " +
                            "языковой пакет «${language.name}»?\n\n" +

                            "Объём пакета: около " +
                            formatStorageSize(
                                APPROX_MODEL_SIZE_BYTES
                            ) +
                            "\n" +

                            "Свободное место: " +
                            formatStorageSize(
                                freeStorageForDialog
                            )
                )
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        pendingInstallLanguage =
                            null

                        installingLanguage =
                            language


                        val model =
                            TranslateRemoteModel
                                .Builder(language.code)
                                .build()


                        val conditions =
                            DownloadConditions
                                .Builder()
                                .build()

                        /*
                         * RemoteModelManager не сообщает процент загрузки.
                         * Поэтому ставим диагностический таймаут: через
                         * 60 секунд проверяем, установлена ли модель реально.
                         */
                        installTimeoutJob?.cancel()

                        installTimeoutJob =
                            coroutineScope.launch {

                                delay(60_000L)

                                if (
                                    installingLanguage?.code ==
                                    language.code
                                ) {

                                    modelManager
                                        .isModelDownloaded(model)
                                        .addOnSuccessListener { downloaded ->

                                            if (downloaded) {

                                                downloadedLanguages =
                                                    downloadedLanguages +
                                                            language.code

                                                installingLanguage =
                                                    null

                                                targetLanguage =
                                                    language.code

                                                autoTranslateJob
                                                    ?.cancel()

                                                translationRequestId +=
                                                    1

                                            } else {

                                                installingLanguage =
                                                    null

                                                installErrorMessage =
                                                    "Загрузка языкового пакета " +
                                                            "«${language.name}» не завершилась " +
                                                            "за 60 секунд.\n\n" +
                                                            "Проверьте подключение к интернету " +
                                                            "и повторите попытку."
                                            }
                                        }
                                        .addOnFailureListener { exception ->

                                            installingLanguage =
                                                null

                                            installErrorMessage =
                                                "Не удалось проверить состояние " +
                                                        "загрузки:\n\n" +
                                                        (
                                                                exception.message
                                                                    ?: "Неизвестная ошибка"
                                                                )
                                        }
                                }
                            }

                        modelManager
                            .download(
                                model,
                                conditions
                            )
                            .addOnSuccessListener {

                                installTimeoutJob?.cancel()

                                downloadedLanguages =
                                    downloadedLanguages +
                                            language.code

                                installingLanguage =
                                    null

                                /*
                                 * После успешной установки
                                 * сразу выбираем новый язык.
                                 */
                                targetLanguage =
                                    language.code

                                autoTranslateJob
                                    ?.cancel()

                                translationRequestId +=
                                    1
                            }
                            .addOnFailureListener { exception ->

                                installTimeoutJob?.cancel()

                                installingLanguage =
                                    null

                                installErrorMessage =
                                    "Не удалось установить " +
                                            "язык «${language.name}».\n\n" +
                                            (
                                                    exception.message
                                                        ?: "Неизвестная ошибка"
                                                    )
                            }
                    }
                ) {
                    Text("Установить")
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        pendingInstallLanguage =
                            null
                    }
                ) {
                    Text("Не устанавливать")
                }
            }
        )
    }
    insufficientSpaceLanguage?.let { language ->

        AlertDialog(
            onDismissRequest = {
                insufficientSpaceLanguage =
                    null
            },

            title = {
                Text(
                    "Недостаточно свободного места"
                )
            },

            text = {

                Text(
                    "Язык: ${language.name}\n\n" +

                            "Для языковой модели требуется " +
                            "около " +
                            formatStorageSize(
                                APPROX_MODEL_SIZE_BYTES
                            ) +
                            ".\n\n" +

                            "Свободное место: " +
                            formatStorageSize(
                                freeStorageForDialog
                            )
                )
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        insufficientSpaceLanguage =
                            null
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
    installingLanguage?.let { language ->

        AlertDialog(
            onDismissRequest = {
                /*
                 * Пока не разрешаем закрыть окно:
                 * модель действительно скачивается.
                 */
            },

            title = {
                Text("Установка")
            },

            text = {

                Row(
                    verticalAlignment =
                    Alignment.CenterVertically
                ) {

                    CircularProgressIndicator(
                        modifier =
                        Modifier.size(28.dp)
                    )

                    Spacer(
                        modifier =
                        Modifier.width(16.dp)
                    )

                    Text(
                        "Устанавливается языковой пакет " +
                                "«${language.name}»..."
                    )
                }
            },

            confirmButton = {}
        )
    }
    installErrorMessage?.let { message ->

        AlertDialog(
            onDismissRequest = {
                installErrorMessage = null
            },

            title = {
                Text("Ошибка установки")
            },

            text = {
                Text(message)
            },

            confirmButton = {

                TextButton(
                    onClick = {
                        installErrorMessage =
                            null
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun ResultPanel(
    outputText: String,
    targetLanguageName: String,
    modifier: Modifier = Modifier
) {

    val scrollState =
        rememberScrollState()

    Card(
        modifier = modifier
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {

            Text(
                text = targetLanguageName,
                style =
                MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = outputText,
                style =
                MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@OptIn(
    ExperimentalFoundationApi::class
)
@Composable
fun ControlPanel(
    sourceLanguage: String,
    targetLanguage: String,
    languages: List<AppLanguage>,
    downloadedLanguages: Set<String>,
    installingLanguageCode: String?,

    layoutEditMode: Boolean,

    controlOrder: List<ControlItem>,

    onMoveControlItem: (
        ControlItem,
        Int
    ) -> Unit,

    onToggleLayoutEditMode: () -> Unit,
    onSwapLanguage: () -> Unit,
    onSelectTargetLanguage: (String) -> Unit,
    onRequestLanguageInstall: (AppLanguage) -> Unit
) {
    val context =
        LocalContext.current

    var languageMenuVisible by remember {
        mutableStateOf(false)
    }

    val sourceName =
        languages
            .firstOrNull {
                it.code == sourceLanguage
            }
            ?.name
            ?: sourceLanguage

    val targetName =
        languages
            .firstOrNull {
                it.code == targetLanguage
            }
            ?.name
            ?: targetLanguage

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),

            horizontalArrangement =
            Arrangement.spacedBy(8.dp),

            verticalAlignment =
            Alignment.CenterVertically
        ) {



            controlOrder.forEach { item ->

                when (item) {

                    ControlItem.LANGUAGE -> {

                        val languageButtonModifier =
                            if (layoutEditMode) {

                                Modifier.fillMaxWidth()

                            } else {

                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        role = Role.Button,

                                        onClick = {
                                            onSwapLanguage()
                                        },

                                        onLongClick = {
                                            languageMenuVisible = true
                                        }
                                    )
                            }

                        EditableControlItem(
                            item = ControlItem.LANGUAGE,
                            editMode = layoutEditMode,

                            modifier =
                            Modifier.weight(1f),

                            onMove = { direction ->

                                onMoveControlItem(
                                    ControlItem.LANGUAGE,
                                    direction
                                )
                            }
                        ) {

                            Box(
                                modifier =
                                Modifier.fillMaxWidth()
                            ) {

                                Surface(
                                    shape =
                                    RoundedCornerShape(12.dp),

                                    tonalElevation = 2.dp,

                                    modifier =
                                    languageButtonModifier
                                ) {

                                    Column(
                                        modifier =
                                        Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 6.dp
                                        ),

                                        horizontalAlignment =
                                        Alignment.CenterHorizontally
                                    ) {

                                        Text(
                                            text = sourceName,

                                            style =
                                            MaterialTheme
                                                .typography
                                                .labelSmall
                                        )

                                        Text(
                                            text = "↔",

                                            style =
                                            MaterialTheme
                                                .typography
                                                .titleLarge
                                        )

                                        Text(
                                            text = targetName,

                                            style =
                                            MaterialTheme
                                                .typography
                                                .labelSmall
                                        )
                                    }
                                }


                                DropdownMenu(
                                    expanded =
                                    languageMenuVisible,

                                    onDismissRequest = {
                                        languageMenuVisible = false
                                    }
                                ) {

                                    val internetAvailable =
                                        isInternetAvailable(context)


                                    languages.forEach { language ->

                                        val installed =
                                            language.code in
                                                    downloadedLanguages

                                        val installing =
                                            language.code ==
                                                    installingLanguageCode


                                        val itemColor =
                                            when {

                                                installed ->
                                                    MaterialTheme
                                                        .colorScheme
                                                        .onSurface

                                                internetAvailable ->
                                                    Color(0xFFFF9800)

                                                else ->
                                                    MaterialTheme
                                                        .colorScheme
                                                        .onSurface
                                                        .copy(alpha = 0.35f)
                                            }


                                        DropdownMenuItem(

                                            text = {

                                                Text(
                                                    text =
                                                    if (installing) {

                                                        "${language.name} — установка..."

                                                    } else {

                                                        language.name
                                                    },

                                                    color =
                                                    itemColor
                                                )
                                            },

                                            enabled =
                                            !installing &&
                                                    (
                                                            installed ||
                                                                    internetAvailable
                                                            ),

                                            onClick = {

                                                when {

                                                    installed -> {

                                                        onSelectTargetLanguage(
                                                            language.code
                                                        )
                                                    }

                                                    internetAvailable -> {

                                                        onRequestLanguageInstall(
                                                            language
                                                        )
                                                    }
                                                }

                                                languageMenuVisible =
                                                    false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }


                    ControlItem.SETTINGS -> {

                        EditableControlItem(
                            item =
                            ControlItem.SETTINGS,

                            editMode =
                            layoutEditMode,

                            modifier =
                            Modifier.weight(1f),

                            onMove = { direction ->

                                onMoveControlItem(
                                    ControlItem.SETTINGS,
                                    direction
                                )
                            }
                        ) {

                            OutlinedButton(
                                onClick =
                                onToggleLayoutEditMode,

                                modifier =
                                Modifier.fillMaxWidth()
                            ) {

                                Text(
                                    if (layoutEditMode) {
                                        "✓"
                                    } else {
                                        "⚙"
                                    }
                                )
                            }
                        }
                    }


                    ControlItem.ADD_LANGUAGE -> {

                        EditableControlItem(
                            item =
                            ControlItem.ADD_LANGUAGE,

                            editMode =
                            layoutEditMode,

                            modifier =
                            Modifier.weight(1f),

                            onMove = { direction ->

                                onMoveControlItem(
                                    ControlItem.ADD_LANGUAGE,
                                    direction
                                )
                            }
                        ) {

                            OutlinedButton(
                                onClick = {

                                    if (!layoutEditMode) {
                                        languageMenuVisible = true
                                    }
                                },

                                modifier =
                                Modifier.fillMaxWidth()
                            ) {

                                Text("+")
                            }
                        }
                    }


                    ControlItem.ENGINE -> {

                        EditableControlItem(
                            item =
                            ControlItem.ENGINE,

                            editMode =
                            layoutEditMode,

                            modifier =
                            Modifier.weight(1f),

                            onMove = { direction ->

                                onMoveControlItem(
                                    ControlItem.ENGINE,
                                    direction
                                )
                            }
                        ) {

                            OutlinedButton(
                                onClick = {},
                                enabled = false,

                                modifier =
                                Modifier.fillMaxWidth()
                            ) {

                                Text("ML")
                            }
                        }
                    }
                }
            }
            // ============================================================
        }
    }
}

@Composable
fun InputPanel(
    inputText: String,

    layoutEditMode: Boolean,

    inputOrder: List<InputItem>,

    onMoveInputItem: (
        InputItem,
        Int
    ) -> Unit,

    onInputTextChange: (String) -> Unit,
    isLoading: Boolean,
    compact: Boolean,
    onTranslate: () -> Unit
) {

    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = inputText,
                selection = TextRange(inputText.length)
            )
        )
    }

    /*
     * Если inputText изменился извне
     * (например, после смены направления),
     * обновляем поле и ставим курсор в конец.
     */
    LaunchedEffect(inputText) {

        if (inputText != fieldValue.text) {

            fieldValue =
                TextFieldValue(
                    text = inputText,
                    selection = TextRange(inputText.length)
                )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    if (compact) 8.dp
                    else 12.dp
                ),

            horizontalArrangement =
            Arrangement.spacedBy(8.dp),

            verticalAlignment =
            Alignment.Bottom
        ) {

            inputOrder.forEach { item ->

                when (item) {

                    InputItem.TEXT_FIELD -> {

                        EditableInputItem(
                            item =
                            InputItem.TEXT_FIELD,

                            editMode =
                            layoutEditMode,

                            modifier =
                            Modifier.weight(1f),

                            onMove = { direction ->

                                onMoveInputItem(
                                    InputItem.TEXT_FIELD,
                                    direction
                                )
                            }
                        ) {

                            OutlinedTextField(
                                value = fieldValue,

                                onValueChange = { newValue ->

                                    fieldValue =
                                        newValue

                                    onInputTextChange(
                                        newValue.text
                                    )
                                },

                                modifier =
                                Modifier.fillMaxWidth(),

                                label = {
                                    Text("Введите текст")
                                },

                                minLines =
                                if (compact) 2
                                else 3,

                                maxLines =
                                if (compact) 3
                                else 6
                            )
                        }
                    }


                    InputItem.TRANSLATE -> {

                        EditableInputItem(
                            item =
                            InputItem.TRANSLATE,

                            editMode =
                            layoutEditMode,

                            onMove = { direction ->

                                onMoveInputItem(
                                    InputItem.TRANSLATE,
                                    direction
                                )
                            }
                        ) {

                            Button(
                                onClick =
                                onTranslate,

                                enabled =
                                !isLoading,

                                modifier =
                                Modifier.size(
                                    if (compact) 52.dp
                                    else 56.dp
                                ),

                                shape =
                                RoundedCornerShape(16.dp),

                                contentPadding =
                                PaddingValues(0.dp)
                            ) {

                                if (isLoading) {

                                    CircularProgressIndicator(
                                        modifier =
                                        Modifier.size(22.dp),

                                        strokeWidth = 2.dp
                                    )

                                } else {

                                    Text(
                                        text = "→",

                                        style =
                                        MaterialTheme
                                            .typography
                                            .headlineSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun isInternetAvailable(
    context: Context
): Boolean {

    val manager =
        context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

    val network =
        manager.activeNetwork
            ?: return false

    val capabilities =
        manager.getNetworkCapabilities(network)
            ?: return false

    return capabilities.hasCapability(
        NetworkCapabilities.NET_CAPABILITY_INTERNET
    ) && capabilities.hasCapability(
        NetworkCapabilities.NET_CAPABILITY_VALIDATED
    )
}

private const val APPROX_MODEL_SIZE_BYTES =
    30L * 1024L * 1024L

/*
 * Так как ML Kit сообщает только примерный размер
 * модели (~30 МБ), для проверки оставляем небольшой запас.
 */
private const val MIN_FREE_SPACE_FOR_MODEL_BYTES =
    40L * 1024L * 1024L


fun getFreeStorageBytes(
    context: Context
): Long {

    val statFs =
        StatFs(
            context.filesDir.absolutePath
        )

    return statFs.availableBytes
}


fun formatStorageSize(
    bytes: Long
): String {

    val mb =
        bytes.toDouble() /
                (1024.0 * 1024.0)

    if (mb < 1024.0) {

        return String.format(
            Locale.getDefault(),
            "%.1f МБ",
            mb
        )
    }

    val gb =
        mb / 1024.0

    return String.format(
        Locale.getDefault(),
        "%.2f ГБ",
        gb
    )
}

enum class LayoutBlock {
    RESULT,
    CONTROL,
    INPUT
}

enum class ControlItem {
    LANGUAGE,
    SETTINGS,
    ADD_LANGUAGE,
    ENGINE
}

enum class InputItem {
    TEXT_FIELD,
    TRANSLATE
}

val DEFAULT_INPUT_ORDER =
    listOf(
        InputItem.TEXT_FIELD,
        InputItem.TRANSLATE
    )

val DEFAULT_CONTROL_ORDER =
    listOf(
        ControlItem.LANGUAGE,
        ControlItem.SETTINGS,
        ControlItem.ADD_LANGUAGE,
        ControlItem.ENGINE
    )

val DEFAULT_LAYOUT_ORDER =
    listOf(
        LayoutBlock.RESULT,
        LayoutBlock.CONTROL,
        LayoutBlock.INPUT
    )


fun loadLayoutOrder(
    context: Context
): List<LayoutBlock> {

    val prefs =
        context.getSharedPreferences(
            "layout_settings",
            Context.MODE_PRIVATE
        )

    val saved =
        prefs.getString(
            "layout_order",
            null
        )
            ?: return DEFAULT_LAYOUT_ORDER

    val result =
        saved
            .split(",")
            .mapNotNull { name ->

                runCatching {
                    LayoutBlock.valueOf(name)
                }.getOrNull()
            }

    return if (
        result.size ==
        DEFAULT_LAYOUT_ORDER.size
    ) {
        result
    } else {
        DEFAULT_LAYOUT_ORDER
    }
}


fun saveLayoutOrder(
    context: Context,
    order: List<LayoutBlock>
) {

    context
        .getSharedPreferences(
            "layout_settings",
            Context.MODE_PRIVATE
        )
        .edit()
        .putString(
            "layout_order",
            order.joinToString(",") {
                it.name
            }
        )
        .apply()
}



@Composable
fun EditableLayoutBlock(
    block: LayoutBlock,
    editMode: Boolean,
    modifier: Modifier = Modifier,
    onMove: (Int) -> Unit,
    content: @Composable () -> Unit
) {

    var dragDistance by remember {
        mutableFloatStateOf(0f)
    }

    val editModifier =
        if (editMode) {

            Modifier
                .border(
                    width = 2.dp,
                    color =
                    MaterialTheme
                        .colorScheme
                        .primary,
                    shape =
                    RoundedCornerShape(12.dp)
                )
                .pointerInput(
                    block,
                    editMode
                ) {

                    val directionThreshold =
                        8.dp.toPx()

                    val moveThreshold =
                        70.dp.toPx()


                    awaitEachGesture {

                        val down =
                            awaitFirstDown(
                                requireUnconsumed = false
                            )

                        var totalX = 0f
                        var totalY = 0f

                        var isVerticalGesture:
                                Boolean? = null

                        dragDistance = 0f


                        while (true) {

                            val event =
                                awaitPointerEvent()

                            val change =
                                event.changes
                                    .firstOrNull {
                                        it.id == down.id
                                    }
                                    ?: break


                            if (!change.pressed) {
                                break
                            }


                            val deltaX =
                                change.position.x -
                                        change.previousPosition.x

                            val deltaY =
                                change.position.y -
                                        change.previousPosition.y


                            totalX += deltaX
                            totalY += deltaY


                            /*
                             * Сначала определяем,
                             * куда пользователь хочет тащить.
                             */
                            if (
                                isVerticalGesture == null &&
                                (
                                        abs(totalX) >
                                                directionThreshold ||
                                                abs(totalY) >
                                                directionThreshold
                                        )
                            ) {

                                isVerticalGesture =
                                    abs(totalY) >
                                            abs(totalX)


                                /*
                                 * Это горизонтальный жест.
                                 *
                                 * Его не трогаем:
                                 * пусть EditableControlItem
                                 * обработает его сам.
                                 */
                                if (
                                    isVerticalGesture == false
                                ) {
                                    break
                                }
                            }


                            if (
                                isVerticalGesture == true
                            ) {

                                change.consume()

                                dragDistance +=
                                    deltaY


                                if (
                                    abs(dragDistance) >
                                    moveThreshold
                                ) {

                                    if (
                                        dragDistance < 0
                                    ) {

                                        onMove(-1)

                                    } else {

                                        onMove(1)
                                    }

                                    dragDistance = 0f
                                }
                            }
                        }

                        dragDistance = 0f
                    }
                }

        } else {

            Modifier
        }


    Box(
        modifier =
        modifier.then(
            editModifier
        )
    ) {

        content()

        if (editMode) {

            Text(
                text = "≡",

                style =
                MaterialTheme
                    .typography
                    .titleLarge,

                modifier =
                Modifier
                    .align(
                        Alignment.TopEnd
                    )
                    .padding(6.dp)
            )
        }
    }
}

fun loadControlOrder(
    context: Context
): List<ControlItem> {

    val prefs =
        context.getSharedPreferences(
            "layout_settings",
            Context.MODE_PRIVATE
        )

    val saved =
        prefs.getString(
            "control_order",
            null
        )
            ?: return DEFAULT_CONTROL_ORDER

    val result =
        saved
            .split(",")
            .mapNotNull { name ->

                runCatching {
                    ControlItem.valueOf(name)
                }.getOrNull()
            }

    return if (
        result.size ==
        DEFAULT_CONTROL_ORDER.size
    ) {
        result
    } else {
        DEFAULT_CONTROL_ORDER
    }
}


fun saveControlOrder(
    context: Context,
    order: List<ControlItem>
) {

    context
        .getSharedPreferences(
            "layout_settings",
            Context.MODE_PRIVATE
        )
        .edit()
        .putString(
            "control_order",
            order.joinToString(",") {
                it.name
            }
        )
        .apply()
}

@Composable
fun EditableControlItem(
    item: ControlItem,
    editMode: Boolean,
    modifier: Modifier = Modifier,
    onMove: (Int) -> Unit,
    content: @Composable () -> Unit
) {

    var dragDistance by remember {
        mutableFloatStateOf(0f)
    }

    val dragModifier =
        if (editMode) {

            Modifier.pointerInput(
                item,
                editMode
            ) {

                val directionThreshold =
                    8.dp.toPx()

                val moveThreshold =
                    45.dp.toPx()


                awaitEachGesture {

                    val down =
                        awaitFirstDown(
                            requireUnconsumed = false
                        )

                    var totalX = 0f
                    var totalY = 0f

                    var isHorizontalGesture:
                            Boolean? = null

                    dragDistance = 0f


                    while (true) {

                        val event =
                            awaitPointerEvent()

                        val change =
                            event.changes
                                .firstOrNull {
                                    it.id == down.id
                                }
                                ?: break


                        if (!change.pressed) {
                            break
                        }


                        val deltaX =
                            change.position.x -
                                    change.previousPosition.x

                        val deltaY =
                            change.position.y -
                                    change.previousPosition.y


                        totalX += deltaX
                        totalY += deltaY


                        /*
                         * Определяем направление жеста.
                         */
                        if (
                            isHorizontalGesture == null &&
                            (
                                    abs(totalX) >
                                            directionThreshold ||
                                            abs(totalY) >
                                            directionThreshold
                                    )
                        ) {

                            isHorizontalGesture =
                                abs(totalX) >
                                        abs(totalY)


                            /*
                             * Если пользователь тянет
                             * вертикально, ничего не
                             * потребляем.
                             *
                             * Жест получит
                             * EditableLayoutBlock.
                             */
                            if (
                                isHorizontalGesture == false
                            ) {
                                break
                            }
                        }


                        if (
                            isHorizontalGesture == true
                        ) {

                            change.consume()

                            dragDistance +=
                                deltaX


                            if (
                                abs(dragDistance) >
                                moveThreshold
                            ) {

                                if (
                                    dragDistance < 0
                                ) {

                                    onMove(-1)

                                } else {

                                    onMove(1)
                                }

                                dragDistance = 0f
                            }
                        }
                    }

                    dragDistance = 0f
                }
            }

        } else {

            Modifier
        }


    Box(
        modifier =
        modifier.then(
            dragModifier
        )
    ) {

        content()
    }
}

fun loadInputOrder(
    context: Context
): List<InputItem> {

    val prefs =
        context.getSharedPreferences(
            "layout_settings",
            Context.MODE_PRIVATE
        )

    val saved =
        prefs.getString(
            "input_order",
            null
        )
            ?: return DEFAULT_INPUT_ORDER

    val result =
        saved
            .split(",")
            .mapNotNull { name ->

                runCatching {
                    InputItem.valueOf(name)
                }.getOrNull()
            }

    return if (
        result.size ==
        DEFAULT_INPUT_ORDER.size
    ) {
        result
    } else {
        DEFAULT_INPUT_ORDER
    }
}


fun saveInputOrder(
    context: Context,
    order: List<InputItem>
) {

    context
        .getSharedPreferences(
            "layout_settings",
            Context.MODE_PRIVATE
        )
        .edit()
        .putString(
            "input_order",
            order.joinToString(",") {
                it.name
            }
        )
        .apply()
}


@Composable
fun EditableInputItem(
    item: InputItem,
    editMode: Boolean,
    modifier: Modifier = Modifier,
    onMove: (Int) -> Unit,
    content: @Composable () -> Unit
) {

    var dragDistance by remember {
        mutableFloatStateOf(0f)
    }


    Box(
        modifier = modifier
    ) {

        content()


        /*
         * В режиме редактирования кладём
         * прозрачный слой поверх элемента.
         *
         * Поэтому TextField не открывает
         * клавиатуру, а кнопка → не запускает
         * перевод.
         */
        if (editMode) {

            Box(
                modifier =
                Modifier
                    .matchParentSize()
                    .pointerInput(
                        item,
                        editMode
                    ) {

                        val directionThreshold =
                            8.dp.toPx()

                        val moveThreshold =
                            45.dp.toPx()


                        awaitEachGesture {

                            val down =
                                awaitFirstDown(
                                    requireUnconsumed =
                                    false
                                )

                            var totalX = 0f
                            var totalY = 0f

                            var horizontal:
                                    Boolean? = null

                            dragDistance = 0f


                            while (true) {

                                val event =
                                    awaitPointerEvent()

                                val change =
                                    event.changes
                                        .firstOrNull {
                                            it.id == down.id
                                        }
                                        ?: break


                                if (!change.pressed) {
                                    break
                                }


                                val deltaX =
                                    change.position.x -
                                            change.previousPosition.x

                                val deltaY =
                                    change.position.y -
                                            change.previousPosition.y


                                totalX += deltaX
                                totalY += deltaY


                                if (
                                    horizontal == null &&
                                    (
                                            abs(totalX) >
                                                    directionThreshold ||
                                                    abs(totalY) >
                                                    directionThreshold
                                            )
                                ) {

                                    horizontal =
                                        abs(totalX) >
                                                abs(totalY)


                                    /*
                                     * Вертикальное движение
                                     * отдаём родительскому
                                     * EditableLayoutBlock.
                                     */
                                    if (
                                        horizontal == false
                                    ) {
                                        break
                                    }
                                }


                                if (
                                    horizontal == true
                                ) {

                                    change.consume()

                                    dragDistance +=
                                        deltaX


                                    if (
                                        abs(dragDistance) >
                                        moveThreshold
                                    ) {

                                        if (
                                            dragDistance < 0
                                        ) {
                                            onMove(-1)
                                        } else {
                                            onMove(1)
                                        }

                                        dragDistance = 0f
                                    }
                                }
                            }

                            dragDistance = 0f
                        }
                    }
            )
        }
    }
}