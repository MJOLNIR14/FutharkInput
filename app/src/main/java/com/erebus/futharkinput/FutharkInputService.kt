package com.erebus.futharkinput

import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

@Suppress("DEPRECATION")
class FutharkInputService : InputMethodService(),
    KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var runeKeyboard: Keyboard
    private lateinit var qwertyKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard
    private lateinit var runeQwertyKeyboard: Keyboard

    private var isRuneMode      = true
    private var isSymbolMode    = false
    private var isRuneQwertyMode = false
    private var isCapsOn        = false
    private var isCapsLocked    = false

    private var runePopup: PopupWindow? = null
    private var lastPressedRune = 0
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private val LONG_PRESS_MS = 400L
    private var ignoreNextKey = false

    companion object {
        const val CODE_SWITCH_RUNE   = 1001
        const val CODE_SYSTEM_PICKER = 1002
        const val CODE_SHIFT         = 1003
        const val CODE_SYMBOLS       = 1004
        const val CODE_SYMBOLS_BACK  = 1005
        const val CODE_SWITCH_RUNE2  = 1006

        val LONG_PRESS_SYMBOLS = mapOf(
            113 to "@",  119 to "&",  101 to "€",  114 to "4",  116 to "5",
            121 to "6",  117 to "7",  105 to "8",  111 to "9",  112 to "0",
            97  to "*",  115 to "#",  100 to "$",  102 to "%",  103 to "^",
            104 to "-",  106 to "+",  107 to "(",  108 to ")",
            122 to "~",  120 to "!",  99  to "?",  118 to "/",
            98  to ";",  110 to "'",  109 to "\""
        )

        val RUNE_INFO = mapOf(
            5792 to arrayOf("Fehu",     "F",  "Wealth",      "Cattle, abundance, prosperity and material gain."),
            5794 to arrayOf("Uruz",     "U",  "Strength",    "Wild ox, raw power, physical health and vitality."),
            5798 to arrayOf("Thurisaz", "TH", "Thorn",       "Giant, conflict, the force of chaos and destruction."),
            5800 to arrayOf("Ansuz",    "A",  "God",         "Breath, mouth, divine inspiration and communication."),
            5809 to arrayOf("Raidho",   "R",  "Journey",     "Wagon, riding, right action and ordered movement."),
            5810 to arrayOf("Kenaz",    "K",  "Torch",       "Fire, light in darkness, knowledge and creativity."),
            5815 to arrayOf("Gebo",     "G",  "Gift",        "Generosity, exchange, sacred partnerships and sacrifice."),
            5817 to arrayOf("Wunjo",    "W",  "Joy",         "Harmony, belonging, the joy of clan and kinship."),
            5818 to arrayOf("Hagalaz",  "H",  "Hail",        "Disruption, hailstorm, transformation through crisis."),
            5822 to arrayOf("Naudiz",   "N",  "Need",        "Constraint, necessity, endurance under hardship."),
            5825 to arrayOf("Isa",      "I",  "Ice",         "Stillness, stasis, the frozen pause before change."),
            5827 to arrayOf("Jera",     "J",  "Year",        "Harvest, natural cycles, patient reward for effort."),
            5831 to arrayOf("Eihwaz",   "EI", "Yew",         "Yew tree, the axis of worlds, death and endurance."),
            5832 to arrayOf("Perthro",  "P",  "Fate",        "Mystery, hidden things, chance and the wyrd."),
            5833 to arrayOf("Algiz",    "Z",  "Protection",  "Elk, shield, divine protection and defense."),
            5834 to arrayOf("Sowilo",   "S",  "Sun",         "Solar wheel, success, wholeness and victory."),
            5839 to arrayOf("Tiwaz",    "T",  "Victory",     "The god Tyr, justice, self-sacrifice for a higher cause."),
            5842 to arrayOf("Berkanan", "B",  "Birch",       "Birch tree, growth, rebirth and the nurturing mother."),
            5846 to arrayOf("Ehwaz",    "E",  "Horse",       "Horse and rider, trust, swift and harmonious movement."),
            5847 to arrayOf("Mannaz",   "M",  "Man",         "Humanity, the self, memory and the rational mind."),
            5850 to arrayOf("Laguz",    "L",  "Water",       "Lake, flow, the deep unconscious and intuition."),
            5852 to arrayOf("Ingwaz",   "NG", "Fertility",   "The god Ing, stored potential, gestation and peace."),
            5854 to arrayOf("Dagaz",    "D",  "Dawn",        "Daylight, breakthrough, the balance of opposites."),
            5855 to arrayOf("Othalan",  "O",  "Heritage",    "Ancestral land, inheritance, the sacred enclosure.")
        )
    }

    // ---------- lifecycle ----------

    override fun onCreateInputView(): View {
        keyboardView      = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        runeKeyboard      = Keyboard(this, R.xml.rune_keyboard)
        qwertyKeyboard    = Keyboard(this, R.xml.qwerty_keyboard)
        symbolsKeyboard   = Keyboard(this, R.xml.symbols_keyboard)
        runeQwertyKeyboard = Keyboard(this, R.xml.rune_qwerty_keyboard)

        keyboardView.keyboard = runeKeyboard
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.isPreviewEnabled = false

        keyboardView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    ignoreNextKey = false
                    val key = getPressedKey(event.x.toInt(), event.y.toInt())
                    if (key != null) {
                        val code = key.codes.firstOrNull() ?: -1
                        if ((isRuneMode || isRuneQwertyMode) && RUNE_INFO.containsKey(code)) {
                            lastPressedRune = code
                            longPressRunnable = Runnable { showRunePopup(lastPressedRune) }
                            longPressHandler.postDelayed(longPressRunnable!!, LONG_PRESS_MS)
                        } else if (!isRuneMode && LONG_PRESS_SYMBOLS.containsKey(code)) {
                            longPressRunnable = Runnable { onLongPress(key) }
                            longPressHandler.postDelayed(longPressRunnable!!, LONG_PRESS_MS)
                        }
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelLongPress()
                    dismissRunePopup()
                    false
                }
                else -> false
            }
        }

        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.keyboard = when {
            isRuneMode       -> runeKeyboard
            isRuneQwertyMode -> runeQwertyKeyboard
            isSymbolMode     -> symbolsKeyboard
            else             -> qwertyKeyboard
        }
        keyboardView.closing()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        dismissRunePopup()
        cancelLongPress()
    }

    // ---------- rune popup ----------

    private fun getPressedKey(x: Int, y: Int): Keyboard.Key? {
        val kb = keyboardView.keyboard ?: return null
        return kb.keys.minByOrNull { key ->
            val cx = key.x + key.width / 2
            val cy = key.y + key.height / 2
            val dx = (x - cx).toDouble()
            val dy = (y - cy).toDouble()
            dx * dx + dy * dy
        }?.takeIf { key ->
            x >= key.x && x <= key.x + key.width &&
                    y >= key.y && y <= key.y + key.height
        }
    }

    private fun showRunePopup(code: Int) {
        val info = RUNE_INFO[code] ?: return
        dismissRunePopup()

        val rune      = code.toChar().toString()
        val name      = info[0]
        val phonetic  = info[1]
        val meaning   = info[2]
        val desc      = info[3]

        val formattedText = "$rune  $name  ·  /$phonetic/\n$meaning\n$desc"

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        container.addView(TextView(this).apply {
            text = rune
            textSize = 48f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        })

        container.addView(TextView(this).apply {
            text = "$name  ·  /$phonetic/"
            textSize = 18f
            setTextColor(Color.parseColor("#CCCCCC"))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 4)
        })

        container.addView(TextView(this).apply {
            text = meaning
            textSize = 14f
            setTextColor(Color.parseColor("#EF9F27"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        })

        container.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(0, 8, 0, 12) }
        })

        container.addView(TextView(this).apply {
            text = desc
            textSize = 13f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
        })

        container.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(0, 16, 0, 12) }
        })

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        buttonRow.addView(TextView(this).apply {
            text = "INSERT  $rune"
            textSize = 12f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            setOnClickListener {
                currentInputConnection?.commitText(rune, 1)
                dismissRunePopup()
            }
        })

        buttonRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(16, 1)
        })

        buttonRow.addView(TextView(this).apply {
            text = "INSERT MEANING"
            textSize = 12f
            setTextColor(Color.parseColor("#EF9F27"))
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            setOnClickListener {
                currentInputConnection?.commitText(formattedText, 1)
                dismissRunePopup()
            }
        })

        container.addView(buttonRow)

        runePopup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            elevation = 12f
            showAtLocation(
                keyboardView,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                0,
                keyboardView.height + 16
            )
        }
    }

    private fun dismissRunePopup() {
        runePopup?.dismiss()
        runePopup = null
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    // ---------- caps ----------

    private fun updateCapsKeys() {
        val upper = isCapsOn || isCapsLocked
        qwertyKeyboard.keys.forEach { key ->
            val c = key.codes.firstOrNull() ?: return@forEach
            if (c in 97..122) {
                key.label = if (upper) c.toChar().uppercaseChar().toString()
                else c.toChar().toString()
            }
        }
        qwertyKeyboard.keys.find { it.codes.firstOrNull() == CODE_SHIFT }?.label =
            when {
                isCapsLocked -> "⇪"
                isCapsOn     -> "⇧"
                else         -> "⇧"
            }
        keyboardView.invalidateAllKeys()
    }

    // ---------- key events ----------

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        if (ignoreNextKey) {
            ignoreNextKey = false
            return
        }
        cancelLongPress()
        when (primaryCode) {
            CODE_SWITCH_RUNE -> {
                when {
                    isRuneMode && !isRuneQwertyMode -> {
                        isRuneMode = false
                        isRuneQwertyMode = true
                        isSymbolMode = false
                        keyboardView.keyboard = runeQwertyKeyboard
                    }
                    isRuneQwertyMode -> {
                        isRuneQwertyMode = false
                        isRuneMode = false
                        keyboardView.keyboard = qwertyKeyboard
                    }
                    else -> {
                        isRuneMode = true
                        isRuneQwertyMode = false
                        isSymbolMode = false
                        keyboardView.keyboard = runeKeyboard
                    }
                }
                keyboardView.closing()
            }
            CODE_SYSTEM_PICKER ->
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            CODE_SHIFT -> {
                when {
                    isCapsLocked -> { isCapsLocked = false; isCapsOn = false }
                    isCapsOn     -> { isCapsLocked = true }
                    else         -> { isCapsOn = true }
                }
                updateCapsKeys()
            }
            CODE_SYMBOLS -> {
                isSymbolMode = true
                keyboardView.keyboard = symbolsKeyboard
                keyboardView.closing()
            }
            CODE_SYMBOLS_BACK -> {
                isSymbolMode = false
                keyboardView.keyboard = qwertyKeyboard
                keyboardView.closing()
            }
            -5 -> {
                val ic = currentInputConnection ?: return
                if (ic.getSelectedText(0).isNullOrEmpty())
                    ic.deleteSurroundingTextInCodePoints(1, 0)
                else
                    ic.commitText("", 1)
            }
            32 -> currentInputConnection?.commitText(" ", 1)
            10 -> {
                val ic = currentInputConnection ?: return
                val imeOptions = currentInputEditorInfo?.imeOptions ?: 0
                val action = imeOptions and EditorInfo.IME_MASK_ACTION

                when (action) {
                    EditorInfo.IME_ACTION_NONE,
                    EditorInfo.IME_ACTION_UNSPECIFIED -> {
                        ic.commitText("\n", 1)
                    }
                    else -> {
                        // Only perform the action if the field is NOT multiline
                        val isMultiline = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                        if (isMultiline) {
                            ic.commitText("\n", 1)
                        } else {
                            ic.performEditorAction(action)
                        }
                    }
                }
            }
            else -> {
                val ic = currentInputConnection ?: return
                if (RUNE_INFO.containsKey(primaryCode)) {
                    ic.commitText(primaryCode.toChar().toString(), 1)
                } else {
                    val char = primaryCode.toChar().toString()
                    ic.commitText(
                        if (isCapsOn || isCapsLocked) char.uppercase() else char, 1
                    )
                    if (isCapsOn && !isCapsLocked) {
                        isCapsOn = false
                        updateCapsKeys()
                    }
                }
            }
        }
    }

    private fun onLongPress(key: Keyboard.Key?): Boolean {
        if (key == null || isRuneMode) return false
        val code = key.codes.firstOrNull() ?: return false
        val symbol = LONG_PRESS_SYMBOLS[code] ?: return false
        currentInputConnection?.commitText(symbol, 1)
        ignoreNextKey = true
        return true
    }

    override fun onText(text: CharSequence?) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}