package com.geminidict.app.data.tts

data class NeuralVoiceModelItem(
    val id: String,
    val name: String,
    val accent: String, // "en-US", "en-GB" or "fr-FR"
    val description: String,
    val approxSize: String,
    val approxSizeMb: Int,
    val onnxFileName: String,
    val downloadUrl: String
)

/**
 * Verified Piper VITS English neural TTS models from k2-fsa/sherpa-onnx releases.
 * Each archive bundles model.onnx, tokens.txt, and espeak-ng-data/ for 100% offline synthesis.
 * French voices use the same VITS mechanism (espeak-ng-data covers French phonemes).
 */
object NeuralVoiceCatalog {
    val items: List<NeuralVoiceModelItem> = listOf(
        NeuralVoiceModelItem(
            id = "vits_piper_fr_fr_siwis_medium",
            name = "法语女声 · Siwis（标准法语推荐）",
            accent = "fr-FR",
            description = "高自然度标准法语女声（SIWIS 语料，22kHz），单词与长例句均接近真人朗读。",
            approxSize = "~64 MB",
            approxSizeMb = 64,
            onnxFileName = "fr_FR-siwis-medium.onnx",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fr_FR-siwis-medium.tar.bz2"
        ),
        NeuralVoiceModelItem(
            id = "vits_piper_fr_fr_tom_medium",
            name = "法语男声 · Tom（标准法语）",
            accent = "fr-FR",
            description = "自然沉稳的标准法语男声，适合法语单词与长句离线朗读。",
            approxSize = "~64 MB",
            approxSizeMb = 64,
            onnxFileName = "fr_FR-tom-medium.onnx",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fr_FR-tom-medium.tar.bz2"
        ),
        NeuralVoiceModelItem(
            id = "vits_piper_en_gb_cori_medium",
            name = "英式女声 · Cori（标准英音推荐）",
            accent = "en-GB",
            description = "高自然度标准英式英语女声（RP 口音，22kHz），单词与长例句均接近真人朗读。",
            approxSize = "~63 MB",
            approxSizeMb = 63,
            onnxFileName = "en_GB-cori-medium.onnx",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-cori-medium.tar.bz2"
        ),
        NeuralVoiceModelItem(
            id = "vits_piper_en_gb_alba_medium",
            name = "英式女声 · Alba（温润苏格兰英音）",
            accent = "en-GB",
            description = "清晰温润的英式英语女声（苏格兰口音），听感柔和自然。",
            approxSize = "~63 MB",
            approxSizeMb = 63,
            onnxFileName = "en_GB-alba-medium.onnx",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-alba-medium.tar.bz2"
        ),
        NeuralVoiceModelItem(
            id = "vits_piper_en_gb_alan_medium",
            name = "英式男声 · Alan（沉稳英音）",
            accent = "en-GB",
            description = "自然沉稳的标准英式英语男声，适合英音单词与长句离线朗读。",
            approxSize = "~63 MB",
            approxSizeMb = 63,
            onnxFileName = "en_GB-alan-medium.onnx",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-alan-medium.tar.bz2"
        ),
        NeuralVoiceModelItem(
            id = "vits_piper_en_us_lessac_medium",
            name = "美式女声 · Lessac（高保真推荐）",
            accent = "en-US",
            description = "Piper 最受欢迎的高自然度美式女声，单词与长例句均接近真人朗读。",
            approxSize = "~63 MB",
            approxSizeMb = 63,
            onnxFileName = "en_US-lessac-medium.onnx",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2"
        ),
        NeuralVoiceModelItem(
            id = "vits_piper_en_us_amy_low",
            name = "美式女声 · Amy（快速合成）",
            accent = "en-US",
            description = "低延迟美式英语女声，合成速度快，适合高频查词朗读。",
            approxSize = "~67 MB",
            approxSizeMb = 67,
            onnxFileName = "en_US-amy-low.onnx",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2"
        )
    )
}
