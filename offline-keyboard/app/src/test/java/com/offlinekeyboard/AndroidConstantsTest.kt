package com.offlinekeyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.offlinekeyboard.core.input.FieldInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** :core copies Android constants so it can be tested without the SDK; make sure the copies are right. */
class AndroidConstantsTest {

    @Test
    fun inputTypeConstantsMatch() {
        assertEquals(InputType.TYPE_NULL, FieldInfo.TYPE_NULL)
        assertEquals(InputType.TYPE_MASK_CLASS, FieldInfo.TYPE_MASK_CLASS)
        assertEquals(InputType.TYPE_MASK_VARIATION, FieldInfo.TYPE_MASK_VARIATION)
        assertEquals(InputType.TYPE_CLASS_TEXT, FieldInfo.TYPE_CLASS_TEXT)
        assertEquals(InputType.TYPE_CLASS_NUMBER, FieldInfo.TYPE_CLASS_NUMBER)
        assertEquals(InputType.TYPE_CLASS_PHONE, FieldInfo.TYPE_CLASS_PHONE)
        assertEquals(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, FieldInfo.TYPE_TEXT_FLAG_CAP_SENTENCES)
        assertEquals(InputType.TYPE_TEXT_FLAG_MULTI_LINE, FieldInfo.TYPE_TEXT_FLAG_MULTI_LINE)
        assertEquals(InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE, FieldInfo.TYPE_TEXT_FLAG_IME_MULTI_LINE)
        assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD, FieldInfo.TYPE_TEXT_VARIATION_PASSWORD)
        assertEquals(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, FieldInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        assertEquals(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, FieldInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        assertEquals(InputType.TYPE_NUMBER_VARIATION_PASSWORD, FieldInfo.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    @Test
    fun editorInfoConstantsMatch() {
        assertEquals(EditorInfo.IME_MASK_ACTION, FieldInfo.IME_MASK_ACTION)
        assertEquals(EditorInfo.IME_ACTION_UNSPECIFIED, FieldInfo.IME_ACTION_UNSPECIFIED)
        assertEquals(EditorInfo.IME_ACTION_NONE, FieldInfo.IME_ACTION_NONE)
        assertEquals(EditorInfo.IME_ACTION_GO, FieldInfo.IME_ACTION_GO)
        assertEquals(EditorInfo.IME_ACTION_SEARCH, FieldInfo.IME_ACTION_SEARCH)
        assertEquals(EditorInfo.IME_ACTION_SEND, FieldInfo.IME_ACTION_SEND)
        assertEquals(EditorInfo.IME_ACTION_NEXT, FieldInfo.IME_ACTION_NEXT)
        assertEquals(EditorInfo.IME_ACTION_DONE, FieldInfo.IME_ACTION_DONE)
        assertEquals(EditorInfo.IME_ACTION_PREVIOUS, FieldInfo.IME_ACTION_PREVIOUS)
        assertEquals(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, FieldInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertEquals(EditorInfo.IME_FLAG_NO_ENTER_ACTION, FieldInfo.IME_FLAG_NO_ENTER_ACTION)
    }
}
