package com.movtery.zalithlauncher.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.movtery.zalithlauncher.R;
import com.movtery.zalithlauncher.databinding.DialogKeyboardBinding;
import com.movtery.zalithlauncher.ui.view.AnimButton;

import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.Tools;

import java.util.ArrayList;
import java.util.List;

public class KeyboardDialog extends FullScreenDialog implements View.OnClickListener {
    private final DialogKeyboardBinding binding = DialogKeyboardBinding.inflate(getLayoutInflater());
    private final List<View> mSelectedViews = new ArrayList<>(1);
    private final List<Integer> mSelectedKeycodes = new ArrayList<>(1);
    private OnKeycodeSelectListener mOnKeycodeSelectListener;
    private OnMultiKeycodeSelectListener mOnMultiKeycodeSelectListener;
    private boolean showSpecialButtons = true;
    private boolean isGamepadMapper = false;

    public KeyboardDialog(@NonNull Context context) {
        super(context);
    }

    public KeyboardDialog(@NonNull Context context, boolean isGamepadMapper) {
        this(context);
        this.isGamepadMapper = isGamepadMapper;
    }

    public KeyboardDialog setShowSpecialButtons(boolean show) {
        showSpecialButtons = show;
        return this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(binding.getRoot());

        Window window = getWindow();
        if (window != null) {
            int dimension = (int) Tools.dpToPx(getContext().getResources().getDimension(R.dimen._12sdp));

            WindowManager.LayoutParams params = window.getAttributes();
            params.width = Tools.currentDisplayMetrics.widthPixels - 2 * dimension;
            params.height = Tools.currentDisplayMetrics.heightPixels - 2 * dimension;
            window.setAttributes(params);

            window.setGravity(Gravity.CENTER);
        }

        init(showSpecialButtons);
    }

    private void init(boolean showSpecialButtons) {
        binding.close.setOnClickListener(this);
        binding.close1.setOnClickListener(this);
        binding.send.setOnClickListener(this);

        List<View> specialButtons = new ArrayList<>();

        if (isGamepadMapper) {
            specialButtons.add(getKey(getString(R.string.keycode_unspecified)));
            specialButtons.add(getKey(getString(R.string.keycode_mouse_right)));
            specialButtons.add(getKey(getString(R.string.keycode_mouse_middle)));
            specialButtons.add(getKey(getString(R.string.keycode_mouse_left)));
            specialButtons.add(getKey(getString(R.string.keycode_scroll_up)));
            specialButtons.add(getKey(getString(R.string.keycode_scroll_down)));
        } else {
            specialButtons.add(getKey(getString(R.string.keycode_special_keyboard)));
            specialButtons.add(getKey("GUI"));
            specialButtons.add(getKey(getString(R.string.keycode_special_pri)));
            specialButtons.add(getKey(getString(R.string.keycode_special_sec)));
            specialButtons.add(getKey(getString(R.string.keycode_special_mouse)));
            specialButtons.add(getKey(getString(R.string.keycode_special_mid)));
            specialButtons.add(getKey(getString(R.string.keycode_special_scrollup)));
            specialButtons.add(getKey(getString(R.string.keycode_special_scrolldown)));
            specialButtons.add(getKey(getString(R.string.keycode_special_menu)));
        }

        if (showSpecialButtons) {
            //此处如果不是手柄映射模式，那么将反着加入
            int specialCount = isGamepadMapper ? 0 : specialButtons.size() - 1;
            for (View specialButton : specialButtons) {
                int finalSpecialCount = specialCount;
                specialButton.setTag(finalSpecialCount);
                specialButton.setOnClickListener(this);
                if (isGamepadMapper) specialCount += 1;
                else specialCount -= 1;
            }
        }

        int specialOffset = showSpecialButtons
                ? (isGamepadMapper ? (specialButtons.size() - 1) : specialButtons.size())
                : 0;

        if (!isGamepadMapper) {
            bindKeyboardKey(getKey(getString(R.string.keycode_unspecified)), LwjglGlfwKeycode.GLFW_KEY_UNKNOWN, specialOffset);
        }

        bindKeyboardKey(binding.keyboardHome, LwjglGlfwKeycode.GLFW_KEY_HOME, specialOffset);
        bindKeyboardKey(binding.keyboardEsc, LwjglGlfwKeycode.GLFW_KEY_ESCAPE, specialOffset);
        bindKeyboardKey(binding.keyboard0, LwjglGlfwKeycode.GLFW_KEY_0, specialOffset);
        bindKeyboardKey(binding.keyboard1, LwjglGlfwKeycode.GLFW_KEY_1, specialOffset);
        bindKeyboardKey(binding.keyboard2, LwjglGlfwKeycode.GLFW_KEY_2, specialOffset);
        bindKeyboardKey(binding.keyboard3, LwjglGlfwKeycode.GLFW_KEY_3, specialOffset);
        bindKeyboardKey(binding.keyboard4, LwjglGlfwKeycode.GLFW_KEY_4, specialOffset);
        bindKeyboardKey(binding.keyboard5, LwjglGlfwKeycode.GLFW_KEY_5, specialOffset);
        bindKeyboardKey(binding.keyboard6, LwjglGlfwKeycode.GLFW_KEY_6, specialOffset);
        bindKeyboardKey(binding.keyboard7, LwjglGlfwKeycode.GLFW_KEY_7, specialOffset);
        bindKeyboardKey(binding.keyboard8, LwjglGlfwKeycode.GLFW_KEY_8, specialOffset);
        bindKeyboardKey(binding.keyboard9, LwjglGlfwKeycode.GLFW_KEY_9, specialOffset);
        bindKeyboardKey(binding.keyboardUp, LwjglGlfwKeycode.GLFW_KEY_UP, specialOffset);
        bindKeyboardKey(binding.keyboardDown, LwjglGlfwKeycode.GLFW_KEY_DOWN, specialOffset);
        bindKeyboardKey(binding.keyboardLeft, LwjglGlfwKeycode.GLFW_KEY_LEFT, specialOffset);
        bindKeyboardKey(binding.keyboardRight, LwjglGlfwKeycode.GLFW_KEY_RIGHT, specialOffset);
        bindKeyboardKey(binding.keyboardA, LwjglGlfwKeycode.GLFW_KEY_A, specialOffset);
        bindKeyboardKey(binding.keyboardB, LwjglGlfwKeycode.GLFW_KEY_B, specialOffset);
        bindKeyboardKey(binding.keyboardC, LwjglGlfwKeycode.GLFW_KEY_C, specialOffset);
        bindKeyboardKey(binding.keyboardD, LwjglGlfwKeycode.GLFW_KEY_D, specialOffset);
        bindKeyboardKey(binding.keyboardE, LwjglGlfwKeycode.GLFW_KEY_E, specialOffset);
        bindKeyboardKey(binding.keyboardF, LwjglGlfwKeycode.GLFW_KEY_F, specialOffset);
        bindKeyboardKey(binding.keyboardG, LwjglGlfwKeycode.GLFW_KEY_G, specialOffset);
        bindKeyboardKey(binding.keyboardH, LwjglGlfwKeycode.GLFW_KEY_H, specialOffset);
        bindKeyboardKey(binding.keyboardI, LwjglGlfwKeycode.GLFW_KEY_I, specialOffset);
        bindKeyboardKey(binding.keyboardJ, LwjglGlfwKeycode.GLFW_KEY_J, specialOffset);
        bindKeyboardKey(binding.keyboardK, LwjglGlfwKeycode.GLFW_KEY_K, specialOffset);
        bindKeyboardKey(binding.keyboardL, LwjglGlfwKeycode.GLFW_KEY_L, specialOffset);
        bindKeyboardKey(binding.keyboardM, LwjglGlfwKeycode.GLFW_KEY_M, specialOffset);
        bindKeyboardKey(binding.keyboardN, LwjglGlfwKeycode.GLFW_KEY_N, specialOffset);
        bindKeyboardKey(binding.keyboardO, LwjglGlfwKeycode.GLFW_KEY_O, specialOffset);
        bindKeyboardKey(binding.keyboardP, LwjglGlfwKeycode.GLFW_KEY_P, specialOffset);
        bindKeyboardKey(binding.keyboardQ, LwjglGlfwKeycode.GLFW_KEY_Q, specialOffset);
        bindKeyboardKey(binding.keyboardR, LwjglGlfwKeycode.GLFW_KEY_R, specialOffset);
        bindKeyboardKey(binding.keyboardS, LwjglGlfwKeycode.GLFW_KEY_S, specialOffset);
        bindKeyboardKey(binding.keyboardT, LwjglGlfwKeycode.GLFW_KEY_T, specialOffset);
        bindKeyboardKey(binding.keyboardU, LwjglGlfwKeycode.GLFW_KEY_U, specialOffset);
        bindKeyboardKey(binding.keyboardV, LwjglGlfwKeycode.GLFW_KEY_V, specialOffset);
        bindKeyboardKey(binding.keyboardW, LwjglGlfwKeycode.GLFW_KEY_W, specialOffset);
        bindKeyboardKey(binding.keyboardX, LwjglGlfwKeycode.GLFW_KEY_X, specialOffset);
        bindKeyboardKey(binding.keyboardY, LwjglGlfwKeycode.GLFW_KEY_Y, specialOffset);
        bindKeyboardKey(binding.keyboardZ, LwjglGlfwKeycode.GLFW_KEY_Z, specialOffset);
        bindKeyboardKey(binding.keyboardComma, LwjglGlfwKeycode.GLFW_KEY_COMMA, specialOffset);
        bindKeyboardKey(binding.keyboardPeriod, LwjglGlfwKeycode.GLFW_KEY_PERIOD, specialOffset);
        bindKeyboardKey(binding.keyboardLeftAlt, LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT, specialOffset);
        bindKeyboardKey(binding.keyboardRightAlt, LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT, specialOffset);
        bindKeyboardKey(binding.keyboardLeftShift, LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, specialOffset);
        bindKeyboardKey(binding.keyboardRightShift, LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT, specialOffset);
        bindKeyboardKey(binding.keyboardTab, LwjglGlfwKeycode.GLFW_KEY_TAB, specialOffset);
        bindKeyboardKey(binding.keyboardSpace, LwjglGlfwKeycode.GLFW_KEY_SPACE, specialOffset);
        bindKeyboardKey(binding.keyboardEnter, LwjglGlfwKeycode.GLFW_KEY_ENTER, specialOffset);
        bindKeyboardKey(binding.keyboardBackspace, LwjglGlfwKeycode.GLFW_KEY_BACKSPACE, specialOffset);
        bindKeyboardKey(binding.keyboardGrave, LwjglGlfwKeycode.GLFW_KEY_GRAVE_ACCENT, specialOffset);
        bindKeyboardKey(binding.keyboardMinus, LwjglGlfwKeycode.GLFW_KEY_MINUS, specialOffset);
        bindKeyboardKey(binding.keyboardEquals, LwjglGlfwKeycode.GLFW_KEY_EQUAL, specialOffset);
        bindKeyboardKey(binding.keyboardLeftBracket, LwjglGlfwKeycode.GLFW_KEY_LEFT_BRACKET, specialOffset);
        bindKeyboardKey(binding.keyboardRightBracket, LwjglGlfwKeycode.GLFW_KEY_RIGHT_BRACKET, specialOffset);
        bindKeyboardKey(binding.keyboardBackslash, LwjglGlfwKeycode.GLFW_KEY_BACKSLASH, specialOffset);
        bindKeyboardKey(binding.keyboardSemicolon, LwjglGlfwKeycode.GLFW_KEY_SEMICOLON, specialOffset);
        bindKeyboardKey(binding.keyboardApostrophe, LwjglGlfwKeycode.GLFW_KEY_APOSTROPHE, specialOffset);
        bindKeyboardKey(binding.keyboardSlash, LwjglGlfwKeycode.GLFW_KEY_SLASH, specialOffset);
        bindKeyboardKey(binding.keyboardKpAdd, LwjglGlfwKeycode.GLFW_KEY_KP_ADD, specialOffset);
        bindKeyboardKey(binding.keyboardPageUp, LwjglGlfwKeycode.GLFW_KEY_PAGE_UP, specialOffset);
        bindKeyboardKey(binding.keyboardPageDown, LwjglGlfwKeycode.GLFW_KEY_PAGE_DOWN, specialOffset);
        bindKeyboardKey(binding.keyboardLeftCtrl, LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL, specialOffset);
        bindKeyboardKey(binding.keyboardRightCtrl, LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL, specialOffset);
        bindKeyboardKey(binding.keyboardCapslock, LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK, specialOffset);
        bindKeyboardKey(binding.keyboardPause, LwjglGlfwKeycode.GLFW_KEY_PAUSE, specialOffset);
        bindKeyboardKey(binding.keyboardEnd, LwjglGlfwKeycode.GLFW_KEY_END, specialOffset);
        bindKeyboardKey(binding.keyboardInsert, LwjglGlfwKeycode.GLFW_KEY_INSERT, specialOffset);
        bindKeyboardKey(binding.keyboardF1, LwjglGlfwKeycode.GLFW_KEY_F1, specialOffset);
        bindKeyboardKey(binding.keyboardF2, LwjglGlfwKeycode.GLFW_KEY_F2, specialOffset);
        bindKeyboardKey(binding.keyboardF3, LwjglGlfwKeycode.GLFW_KEY_F3, specialOffset);
        bindKeyboardKey(binding.keyboardF4, LwjglGlfwKeycode.GLFW_KEY_F4, specialOffset);
        bindKeyboardKey(binding.keyboardF5, LwjglGlfwKeycode.GLFW_KEY_F5, specialOffset);
        bindKeyboardKey(binding.keyboardF6, LwjglGlfwKeycode.GLFW_KEY_F6, specialOffset);
        bindKeyboardKey(binding.keyboardF7, LwjglGlfwKeycode.GLFW_KEY_F7, specialOffset);
        bindKeyboardKey(binding.keyboardF8, LwjglGlfwKeycode.GLFW_KEY_F8, specialOffset);
        bindKeyboardKey(binding.keyboardF9, LwjglGlfwKeycode.GLFW_KEY_F9, specialOffset);
        bindKeyboardKey(binding.keyboardF10, LwjglGlfwKeycode.GLFW_KEY_F10, specialOffset);
        bindKeyboardKey(binding.keyboardF11, LwjglGlfwKeycode.GLFW_KEY_F11, specialOffset);
        bindKeyboardKey(binding.keyboardF12, LwjglGlfwKeycode.GLFW_KEY_F12, specialOffset);
        bindKeyboardKey(binding.keyboardNumLock, LwjglGlfwKeycode.GLFW_KEY_NUM_LOCK, specialOffset);
        bindKeyboardKey(binding.keyboardKp0, LwjglGlfwKeycode.GLFW_KEY_KP_0, specialOffset);
        bindKeyboardKey(binding.keyboardKp1, LwjglGlfwKeycode.GLFW_KEY_KP_1, specialOffset);
        bindKeyboardKey(binding.keyboardKp2, LwjglGlfwKeycode.GLFW_KEY_KP_2, specialOffset);
        bindKeyboardKey(binding.keyboardKp3, LwjglGlfwKeycode.GLFW_KEY_KP_3, specialOffset);
        bindKeyboardKey(binding.keyboardKp4, LwjglGlfwKeycode.GLFW_KEY_KP_4, specialOffset);
        bindKeyboardKey(binding.keyboardKp5, LwjglGlfwKeycode.GLFW_KEY_KP_5, specialOffset);
        bindKeyboardKey(binding.keyboardKp6, LwjglGlfwKeycode.GLFW_KEY_KP_6, specialOffset);
        bindKeyboardKey(binding.keyboardKp7, LwjglGlfwKeycode.GLFW_KEY_KP_7, specialOffset);
        bindKeyboardKey(binding.keyboardKp8, LwjglGlfwKeycode.GLFW_KEY_KP_8, specialOffset);
        bindKeyboardKey(binding.keyboardKp9, LwjglGlfwKeycode.GLFW_KEY_KP_9, specialOffset);
        bindKeyboardKey(binding.keyboardKpDivide, LwjglGlfwKeycode.GLFW_KEY_KP_DIVIDE, specialOffset);
        bindKeyboardKey(binding.keyboardKpMultiply, LwjglGlfwKeycode.GLFW_KEY_KP_MULTIPLY, specialOffset);
        bindKeyboardKey(binding.keyboardKpSubract, LwjglGlfwKeycode.GLFW_KEY_KP_SUBTRACT, specialOffset);
        bindKeyboardKey(binding.keyboardKpDecimal, LwjglGlfwKeycode.GLFW_KEY_KP_DECIMAL, specialOffset);
        bindKeyboardKey(binding.keyboardKpEnter, LwjglGlfwKeycode.GLFW_KEY_KP_ENTER, specialOffset);

        if (!showSpecialButtons) {
            binding.specialKey.setVisibility(View.GONE);
        }
    }

    private void bindKeyboardKey(View button, short glfwKeycode, int specialOffset) {
        int keycodeIndex = EfficientAndroidLWJGLKeycode.getIndexByValue(glfwKeycode);
        button.setTag(specialOffset + keycodeIndex);
        button.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v == binding.close || v == binding.close1) {
            closeDialog();
        } else if (v == binding.send) {
            if (!mSelectedKeycodes.isEmpty() && mOnMultiKeycodeSelectListener != null) {
                mOnMultiKeycodeSelectListener.onSelect(new ArrayList<>(mSelectedKeycodes));
            }
            closeDialog();
        } else if (v.getTag() != null) {
            int index = (int) v.getTag();
            onKeycodeSelect(v, index);
        }
    }

    private void closeDialog() {
        mSelectedViews.forEach(sv -> sv.setSelected(false));
        mSelectedViews.clear();
        mSelectedKeycodes.clear();
        this.dismiss();
    }

    private AnimButton getKey(String text) {
        AnimButton key = new AnimButton(getContext());
        key.setText(text);
        key.setTextSize(TypedValue.COMPLEX_UNIT_PX, Tools.dpToPx(9.6F));
        binding.specialKey.addView(key);
        return key;
    }

    private String getString(int resId) {
        return getContext().getString(resId);
    }

    private void onKeycodeSelect(View view, int index) {
        if (this.mOnKeycodeSelectListener != null) {
            this.mOnKeycodeSelectListener.onSelect(index);
            dismiss();
        } else if (this.mOnMultiKeycodeSelectListener != null) {
            if (mSelectedViews.contains(view)) {
                mSelectedViews.remove(view);
                mSelectedKeycodes.remove((Object) index);
                view.setSelected(false);
            } else {
                mSelectedViews.add(view);
                mSelectedKeycodes.add(index);
                view.setSelected(true);
            }
        }
    }

    public KeyboardDialog setOnKeycodeSelectListener(OnKeycodeSelectListener listener) {
        if (this.mOnMultiKeycodeSelectListener != null) {
            throw new IllegalStateException("Two listeners should not be initialized at the same time");
        }
        this.mOnKeycodeSelectListener = listener;
        binding.operateLayout.setVisibility(View.GONE);
        binding.close.setVisibility(View.VISIBLE);
        return this;
    }

    public KeyboardDialog setOnMultiKeycodeSelectListener(OnMultiKeycodeSelectListener listener) {
        if (this.mOnKeycodeSelectListener != null) {
            throw new IllegalStateException("Two listeners should not be initialized at the same time");
        }
        this.mOnMultiKeycodeSelectListener = listener;
        binding.operateLayout.setVisibility(View.VISIBLE);
        binding.close.setVisibility(View.GONE);
        return this;
    }

    public interface OnKeycodeSelectListener {
        void onSelect(int index);
    }

    public interface OnMultiKeycodeSelectListener {
        void onSelect(List<Integer> index);
    }
}
