/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.youtube.layout.snackbar

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.registersUsed
import app.morphe.patches.shared.layout.theme.lithoColorHookPatch
import app.morphe.patches.shared.layout.theme.lithoColorOverrideHook
import app.morphe.patches.shared.misc.settings.preference.InputType
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.shared.misc.settings.preference.TextPreference
import app.morphe.patches.shared.misc.spans.addSpanFilter
import app.morphe.patches.shared.misc.spans.inclusiveSpanPatch
import app.morphe.patches.youtube.misc.playservice.is_21_30_or_greater
import app.morphe.patches.youtube.misc.playservice.versionCheckPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/youtube/patches/SnackbarPatch;"
private const val FILTER_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/youtube/patches/spans/SnackbarFilter;"

@Suppress("unused")
val snackbarPatch = bytecodePatch(
    name = "Snackbar",
    description = "Adds options to customize or hide the snackbar."
) {
    dependsOn(
        settingsPatch,
        inclusiveSpanPatch,
        versionCheckPatch,
        lithoColorHookPatch { is_21_30_or_greater }
    )
    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        addSpanFilter(FILTER_CLASS_DESCRIPTOR)
        lithoColorOverrideHook(EXTENSION_CLASS_DESCRIPTOR, "getLithoColor")

        PreferenceScreen.GENERAL.addPreferences(
            PreferenceScreenPreference(
                key = "morphe_snackbar_screen",
                sorting = PreferenceScreenPreference.Sorting.UNSORTED,
                preferences = setOf(
                    SwitchPreference("morphe_custom_snackbar_theme"),
                    TextPreference("morphe_custom_snackbar_corner_radius", inputType = InputType.NUMBER_DECIMAL),
                    TextPreference("morphe_custom_snackbar_color_dark",
                        tag = "app.morphe.extension.shared.settings.preference.ColorPickerPreference",
                        inputType = InputType.TEXT_CAP_CHARACTERS
                    ),
                    TextPreference("morphe_custom_snackbar_color_light",
                        tag = "app.morphe.extension.shared.settings.preference.ColorPickerPreference",
                        inputType = InputType.TEXT_CAP_CHARACTERS
                    ),
                    TextPreference("morphe_custom_snackbar_stroke_color",
                        tag = "app.morphe.extension.shared.settings.preference.ColorPickerPreference",
                        inputType = InputType.TEXT_CAP_CHARACTERS
                    ),
                    TextPreference("morphe_custom_snackbar_text_color",
                        tag = "app.morphe.extension.shared.settings.preference.ColorPickerPreference",
                        inputType = InputType.TEXT_CAP_CHARACTERS
                    ),
                    SwitchPreference("morphe_invert_snackbar_theme"),
                    SwitchPreference("morphe_hide_snackbar"),
                )
            )
        )

        BottomUIContainerPreFingerprint.method.addInstruction(
            BottomUIContainerPreFingerprint.instructionMatches.last().index,
            "invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->onLithoSnackbarPrepare()V"
        )

        LithoSnackbarFingerprint.method.apply {
            val bgIndex = LithoSnackbarFingerprint.instructionMatches[0].index
            val bgInstruction = getInstruction(bgIndex)
            val viewRegister = bgInstruction.registersUsed[0]
            val colorRegister = bgInstruction.registersUsed[1]

            replaceInstruction(
                bgIndex,
                "invoke-static { v$viewRegister, v$colorRegister }, $EXTENSION_CLASS_DESCRIPTOR->" +
                        "setLithoSnackBarBackgroundColor(Landroid/widget/FrameLayout;I)V"
            )

            val castIndex = LithoSnackbarFingerprint.instructionMatches[1].index
            val castRegister = getInstruction<OneRegisterInstruction>(castIndex).registerA
            addInstruction(
                castIndex + 1,
                "invoke-static { v$castRegister }, $EXTENSION_CLASS_DESCRIPTOR->setLithoSnackBarBackground(Landroid/view/View;)V"
            )
        }

        LithoSnackbarLayoutFingerprint.method.apply {
            val contextIndex = LithoSnackbarLayoutFingerprint.instructionMatches[1].index
            val contextRegister = getInstruction<TwoRegisterInstruction>(contextIndex).registerA
            addInstructions(
                contextIndex,
                """
                    invoke-static { v$contextRegister }, $EXTENSION_CLASS_DESCRIPTOR->invertSnackBarTheme(Landroid/content/Context;)Landroid/content/Context;
                    move-result-object v$contextRegister
                """
            )

            val viewIndex = LithoSnackbarLayoutFingerprint.instructionMatches[0].index
            val viewRegister = getInstruction<TwoRegisterInstruction>(viewIndex).registerA
            addInstruction(
                viewIndex,
                "invoke-static { v$viewRegister }, $EXTENSION_CLASS_DESCRIPTOR->hideLithoSnackBar(Landroid/widget/FrameLayout;)V"
            )
        }

        BottomUIContainerFingerprint.method.addInstructionsWithLabels(
            0,
            """
                if-eqz p1, :cleanup
                invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->hideSnackbar()Z
                move-result v0
                if-eqz v0, :show
                return-void
                :show
                nop
                invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->handleLegacySnackbar(Landroid/view/View;)V
                :cleanup
                nop
            """
        )

        BottomUiContainerThemeFingerprint.method.apply {
            val darkThemeMatchIndex = BottomUiContainerThemeFingerprint.instructionMatches[2].index
            val darkThemeReference =
                getInstruction<ReferenceInstruction>(darkThemeMatchIndex).reference.toString()

            val indices = implementation!!.instructions.withIndex()
                .filter { (_, instruction) ->
                    instruction.opcode == Opcode.SGET_OBJECT &&
                            (instruction as? ReferenceInstruction)?.reference?.toString() == darkThemeReference
                }
                .map { it.index }
                .reversed()

            indices.forEach { index ->
                val appThemeIndex = implementation!!.instructions.take(index).withIndex()
                    .last { (_, ins) -> ins.opcode == Opcode.MOVE_RESULT_OBJECT }.index
                val appThemeRegister =
                    getInstruction<OneRegisterInstruction>(appThemeIndex).registerA
                val darkThemeRegister =
                    getInstruction<OneRegisterInstruction>(index).registerA

                addInstructions(
                    index + 1,
                    """
                        invoke-static { v$appThemeRegister, v$darkThemeRegister }, $EXTENSION_CLASS_DESCRIPTOR->invertSnackBarTheme(Ljava/lang/Enum;Ljava/lang/Enum;)Ljava/lang/Enum;
                        move-result-object v$appThemeRegister
                    """
                )
            }
        }

        listOf(
            QuantumSnackbarFingerprint,
            MaterialSnackbarFingerprint,
            AppSnackbarFingerprint,
            YouTubeSnackbarFingerprint,
            MealbarFingerprint
        ).forEach { fingerprint ->
            fingerprint.method.apply {
                addInstruction(
                    fingerprint.instructionMatches.first().index + 1,
                    "invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->handleLegacySnackbar(Landroid/view/View;)V"
                )
            }
        }
    }
}
