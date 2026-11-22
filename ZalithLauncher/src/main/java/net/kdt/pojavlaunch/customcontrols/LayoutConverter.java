package net.kdt.pojavlaunch.customcontrols;

import android.content.Context;
import android.view.KeyEvent;

import com.google.gson.JsonSyntaxException;
import com.movtery.zalithlauncher.R;
import com.movtery.zalithlauncher.ui.subassembly.customcontrols.ControlInfoData;

import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.Tools;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.glfw.CallbackBridge;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LayoutConverter {
    /** MJ Launcher (MojoLauncher) special button for keyboard pan. */
    private static final int MOJO_SPECIALBTN_KEYBOARDPAN = -10;

    /**
     * Reads raw layout JSON from a file, transparently extracting {@code layout.json}
     * when the file is a ZIP-wrapped MJ Launcher (MojoLauncher) control package.
     */
    public static String readLayoutJsonFromFile(File file) throws IOException {
        byte[] bytes;
        try (InputStream is = new FileInputStream(file)) {
            bytes = IOUtils.toByteArray(is);
        }
        if (bytes.length >= 4
                && bytes[0] == 0x50
                && bytes[1] == 0x4B
                && bytes[2] == 0x03
                && bytes[3] == 0x04) {
            String fallbackJson = null;
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    String entryName = entry.getName();
                    if ("layout.json".equalsIgnoreCase(entryName) || entryName.endsWith("/layout.json")) {
                        return stripUtf8Bom(IOUtils.toString(zis, StandardCharsets.UTF_8));
                    }
                    if (fallbackJson == null && entryName.toLowerCase().endsWith(".json")) {
                        fallbackJson = stripUtf8Bom(IOUtils.toString(zis, StandardCharsets.UTF_8));
                    }
                }
            }
            if (fallbackJson != null) {
                return fallbackJson;
            }
        }
        return stripUtf8Bom(new String(bytes, StandardCharsets.UTF_8));
    }

    private static String stripUtf8Bom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    public static CustomControls loadAndConvertIfNecessary(Context ctx, String jsonPath) throws IOException, JsonSyntaxException {
        String jsonLayoutData = readLayoutJsonFromFile(new File(jsonPath));
        try {
            JSONObject layoutJobj = new JSONObject(jsonLayoutData);
            return loadAndConvertIfNecessary(ctx, layoutJobj, jsonLayoutData, jsonPath, true);
        } catch (Exception e) {
            Tools.showError(ctx, ctx.getString(R.string.controls_load_failed), e);
            return null;
        }
    }

    public static CustomControls loadFromAssets(Context context, String jsonName) {
        try (InputStream is = context.getAssets().open(jsonName)) {
            String string = IOUtils.toString(is, StandardCharsets.UTF_8);

            JSONObject layoutJobj = new JSONObject(string);
            return loadAndConvertIfNecessary(context, layoutJobj, string, null, true);
        } catch (Exception e) {
            Tools.showError(context, context.getString(R.string.controls_load_failed), e);
            return null;
        }
    }

    public static CustomControls loadFromJsonObject(Context ctx, JSONObject layoutJobj, String jsonString, String jsonPath, boolean showError) {
        try {
            return loadAndConvertIfNecessary(ctx, layoutJobj, jsonString, jsonPath, showError);
        } catch (Exception e) {
            if (showError) Tools.showError(ctx, ctx.getString(R.string.controls_load_failed), e);
            return null;
        }
    }

    private static CustomControls loadAndConvertIfNecessary(Context ctx, JSONObject layoutJobj, String jsonLayoutData, String jsonPath, boolean showError) throws Exception {
        if (!layoutJobj.has("version")) { //v1 layout
            CustomControls layout = LayoutConverter.convertV1Layout(layoutJobj);
            normalizeLayout(layout, jsonPath);
            if (jsonPath != null) layout.save(jsonPath);
            return layout;
        } else {
            int version = layoutJobj.getInt("version");
            if (version == 2) {
                CustomControls layout = LayoutConverter.convertV2Layout(layoutJobj);
                normalizeLayout(layout, jsonPath);
                if (jsonPath != null) layout.save(jsonPath);
                return layout;
            }
            if (version == 3 || version == 4 || version == 5) {
                CustomControls layout = LayoutConverter.convertV3_4Layout(layoutJobj);
                normalizeLayout(layout, jsonPath);
                return layout;
            }
            if (version == 6 || version == 7) {
                CustomControls layout = convertV6_7Layout(layoutJobj);
                normalizeLayout(layout, jsonPath);
                return layout;
            }
            if (version == 9 || isMojoAndroidKeycodeLayout(layoutJobj, version)) {
                CustomControls layout = convertV9MojoLayout(layoutJobj, jsonPath);
                if (jsonPath != null) layout.save(jsonPath);
                return layout;
            }
            if (version == 8) {
                CustomControls layout = Tools.GLOBAL_GSON.fromJson(jsonLayoutData, CustomControls.class);
                normalizeLayout(layout, jsonPath);
                return layout;
            }
            if (showError && ctx != null) {
                IOException ioException = new IOException();
                Tools.showError(ctx, ctx.getString(R.string.controls_unsupported_layout_version), ioException);
            }
            return null;
        }
    }

    /**
     * Detects MJ Launcher (MojoLauncher) v9 layouts (or v8 layouts exported with Android KeyEvent keycodes).
     */
    public static boolean isMojoAndroidKeycodeLayout(JSONObject layoutJobj, int version) {
        if (version == 9) return true;
        if (version != 8 || layoutJobj.has("mControlInfoDataList")) return false;
        JSONArray buttons = layoutJobj.optJSONArray("mControlDataList");
        if (buttons == null) return false;
        for (int i = 0; i < buttons.length(); i++) {
            JSONObject btn = buttons.optJSONObject(i);
            if (btn == null) continue;
            JSONArray keycodes = btn.optJSONArray("keycodes");
            if (keycodes == null) continue;
            for (int j = 0; j < keycodes.length(); j++) {
                int kc = keycodes.optInt(j, 0);
                if (kc == MOJO_SPECIALBTN_KEYBOARDPAN) return true;
                if (kc > 0 && kc < LwjglGlfwKeycode.GLFW_KEY_SPACE) return true;
            }
        }
        return false;
    }

    /**
     * Converts an MJ Launcher (MojoLauncher) v9 layout (where positive keycodes are Android
     * {@link KeyEvent} constants and {@code -10} is {@code SPECIALBTN_KEYBOARDPAN}) into an
     * Orbit/Zalith v8 layout using {@link LwjglGlfwKeycode} values and populated {@link ControlInfoData}.
     */
    public static CustomControls convertV9MojoLayout(JSONObject oldLayoutJson, String jsonPath) {
        CustomControls layout = Tools.GLOBAL_GSON.fromJson(oldLayoutJson.toString(), CustomControls.class);
        if (layout == null) {
            layout = new CustomControls();
        }
        normalizeLayout(layout, jsonPath);
        for (ControlData button : layout.mControlDataList) {
            if (button != null) {
                convertMojoControlData(button);
            }
        }
        for (ControlDrawerData drawer : layout.mDrawerDataList) {
            if (drawer == null) continue;
            if (drawer.properties != null) {
                convertMojoControlData(drawer.properties);
            }
            if (drawer.buttonProperties != null) {
                for (ControlData subButton : drawer.buttonProperties) {
                    if (subButton != null) {
                        convertMojoControlData(subButton);
                    }
                }
            }
        }
        for (ControlJoystickData joystick : layout.mJoystickDataList) {
            if (joystick != null && joystick.forwardLockKeycode > 0) {
                joystick.forwardLockKeycode = mojoAndroidToGlfw(joystick.forwardLockKeycode);
            }
        }
        layout.version = 8;
        return layout;
    }

    private static void convertMojoControlData(ControlData data) {
        if (data.keycodes == null) {
            data.keycodes = new int[4];
            return;
        }
        for (int i = 0; i < data.keycodes.length; i++) {
            int kc = data.keycodes[i];
            if (kc > 0) {
                data.keycodes[i] = mojoAndroidToGlfw(kc);
            } else if (kc == MOJO_SPECIALBTN_KEYBOARDPAN) {
                data.keycodes[i] = ControlData.SPECIALBTN_KEYBOARD;
            }
        }
    }

    /**
     * Converts an Android {@link KeyEvent} keycode used by MJ Launcher into the corresponding
     * {@link LwjglGlfwKeycode} constant.
     */
    public static int mojoAndroidToGlfw(int androidKeycode) {
        if (androidKeycode <= 0) {
            return androidKeycode == MOJO_SPECIALBTN_KEYBOARDPAN
                    ? ControlData.SPECIALBTN_KEYBOARD
                    : LwjglGlfwKeycode.GLFW_KEY_UNKNOWN;
        }
        int idx = EfficientAndroidLWJGLKeycode.getIndexByKey(androidKeycode);
        if (idx >= 0) {
            return EfficientAndroidLWJGLKeycode.getValueByIndex(idx);
        }
        switch (androidKeycode) {
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return LwjglGlfwKeycode.GLFW_KEY_DELETE;
            case KeyEvent.KEYCODE_SCROLL_LOCK:
                return LwjglGlfwKeycode.GLFW_KEY_SCROLL_LOCK;
            case KeyEvent.KEYCODE_META_LEFT:
                return LwjglGlfwKeycode.GLFW_KEY_LEFT_SUPER;
            case KeyEvent.KEYCODE_META_RIGHT:
                return LwjglGlfwKeycode.GLFW_KEY_RIGHT_SUPER;
            case KeyEvent.KEYCODE_SYSRQ:
                return LwjglGlfwKeycode.GLFW_KEY_PRINT_SCREEN;
            case KeyEvent.KEYCODE_MENU:
                return LwjglGlfwKeycode.GLFW_KEY_MENU;
            default:
                return androidKeycode >= LwjglGlfwKeycode.GLFW_KEY_SPACE
                        ? androidKeycode
                        : LwjglGlfwKeycode.GLFW_KEY_UNKNOWN;
        }
    }

    private static void normalizeLayout(CustomControls layout, String jsonPath) {
        if (layout == null) return;
        if (layout.mControlDataList == null) layout.mControlDataList = new ArrayList<>();
        if (layout.mDrawerDataList == null) layout.mDrawerDataList = new ArrayList<>();
        if (layout.mJoystickDataList == null) layout.mJoystickDataList = new ArrayList<>();
        if (layout.mControlInfoDataList == null) {
            layout.mControlInfoDataList = new ControlInfoData();
        }
        String fallbackFileName = null;
        if (jsonPath != null && !jsonPath.isEmpty()) {
            fallbackFileName = new File(jsonPath).getName();
        }
        if (layout.mControlInfoDataList.fileName == null
                || layout.mControlInfoDataList.fileName.isEmpty()
                || "null".equals(layout.mControlInfoDataList.fileName)) {
            layout.mControlInfoDataList.fileName = fallbackFileName != null ? fallbackFileName : "default.json";
        }
        if (layout.mControlInfoDataList.name == null
                || layout.mControlInfoDataList.name.isEmpty()
                || "null".equals(layout.mControlInfoDataList.name)
                || "empty".equals(layout.mControlInfoDataList.name)) {
            if (fallbackFileName != null) {
                int dot = fallbackFileName.lastIndexOf('.');
                layout.mControlInfoDataList.name = dot > 0 ? fallbackFileName.substring(0, dot) : fallbackFileName;
            } else {
                layout.mControlInfoDataList.name = "empty";
            }
        }
        if (layout.mControlInfoDataList.version == null) layout.mControlInfoDataList.version = "null";
        if (layout.mControlInfoDataList.author == null) layout.mControlInfoDataList.author = "null";
        if (layout.mControlInfoDataList.desc == null) layout.mControlInfoDataList.desc = "null";
        for (ControlData button : layout.mControlDataList) {
            if (button != null && button.keycodes != null) {
                for (int i = 0; i < button.keycodes.length; i++) {
                    if (button.keycodes[i] == MOJO_SPECIALBTN_KEYBOARDPAN) {
                        button.keycodes[i] = ControlData.SPECIALBTN_KEYBOARD;
                    }
                }
            }
        }
    }

    /**
     * Normalize the layout to v8 from v6/7. An issue from the joystick height and position has to be fixed.
     * @param oldLayoutJson The old layout
     * @return The new layout with the fixed joystick height
     */
    public static CustomControls convertV6_7Layout(JSONObject oldLayoutJson) {
        CustomControls layout = Tools.GLOBAL_GSON.fromJson(oldLayoutJson.toString(), CustomControls.class);
        for (ControlJoystickData data : layout.mJoystickDataList) {
            if (data.getHeight() > data.getWidth()) {
                // Make the size square, adjust the dynamic position related to height
                float ratio = data.getHeight() / data.getWidth();
                data.dynamicX = data.dynamicX.replace("${height}", "(" + ratio + " * ${height})");
                data.dynamicY = data.dynamicY.replace("${height}", "(" + ratio + " * ${height})") +  " + (" + (ratio-1) + " * ${height})";
                data.setHeight(data.getWidth());
            }
        }
        layout.version = 8;
        return layout;
    }

    /**
     * Normalize the layout to v6 from v3/4: The stroke width is no longer dependant on the button size
     */
    private static CustomControls convertV3_4Layout(JSONObject oldLayoutJson) {
        CustomControls layout = Tools.GLOBAL_GSON.fromJson(oldLayoutJson.toString(), CustomControls.class);
        convertStrokeWidth(layout);
        layout.version = 6;
        return layout;
    }


    private static CustomControls convertV2Layout(JSONObject oldLayoutJson) throws JSONException {
        CustomControls layout = Tools.GLOBAL_GSON.fromJson(oldLayoutJson.toString(), CustomControls.class);
        JSONArray layoutMainArray = oldLayoutJson.getJSONArray("mControlDataList");
        layout.mControlDataList = new ArrayList<>(layoutMainArray.length());
        for (int i = 0; i < layoutMainArray.length(); i++) {
            JSONObject button = layoutMainArray.getJSONObject(i);
            ControlData n_button = Tools.GLOBAL_GSON.fromJson(button.toString(), ControlData.class);
            if (!Tools.isValidString(n_button.dynamicX) && button.has("x")) {
                double buttonC = button.getDouble("x");
                double ratio = buttonC / CallbackBridge.physicalWidth;
                n_button.dynamicX = ratio + " * ${screen_width}";
            }
            if (!Tools.isValidString(n_button.dynamicY) && button.has("y")) {
                double buttonC = button.getDouble("y");
                double ratio = buttonC / CallbackBridge.physicalHeight;
                n_button.dynamicY = ratio + " * ${screen_height}";
            }
            layout.mControlDataList.add(n_button);
        }
        JSONArray layoutDrawerArray = oldLayoutJson.getJSONArray("mDrawerDataList");
        layout.mDrawerDataList = new ArrayList<>();
        for (int i = 0; i < layoutDrawerArray.length(); i++) {
            JSONObject button = layoutDrawerArray.getJSONObject(i);
            JSONObject buttonProperties = button.getJSONObject("properties");
            ControlDrawerData n_button = Tools.GLOBAL_GSON.fromJson(button.toString(), ControlDrawerData.class);
            if (!Tools.isValidString(n_button.properties.dynamicX) && buttonProperties.has("x")) {
                double buttonC = buttonProperties.getDouble("x");
                double ratio = buttonC / CallbackBridge.physicalWidth;
                n_button.properties.dynamicX = ratio + " * ${screen_width}";
            }
            if (!Tools.isValidString(n_button.properties.dynamicY) && buttonProperties.has("y")) {
                double buttonC = buttonProperties.getDouble("y");
                double ratio = buttonC / CallbackBridge.physicalHeight;
                n_button.properties.dynamicY = ratio + " * ${screen_height}";
            }
            layout.mDrawerDataList.add(n_button);
        }
        convertStrokeWidth(layout);

        layout.version = 3;
        return layout;
    }

    private static CustomControls convertV1Layout(JSONObject oldLayoutJson) throws JSONException {
        CustomControls empty = new CustomControls();
        JSONArray layoutMainArray = oldLayoutJson.getJSONArray("mControlDataList");
        for (int i = 0; i < layoutMainArray.length(); i++) {
            JSONObject button = layoutMainArray.getJSONObject(i);
            ControlData n_button = new ControlData();
            int[] keycodes = new int[]{LwjglGlfwKeycode.GLFW_KEY_UNKNOWN,
                    LwjglGlfwKeycode.GLFW_KEY_UNKNOWN,
                    LwjglGlfwKeycode.GLFW_KEY_UNKNOWN,
                    LwjglGlfwKeycode.GLFW_KEY_UNKNOWN};
            n_button.dynamicX = button.getString("dynamicX");
            n_button.dynamicY = button.getString("dynamicY");
            if (!Tools.isValidString(n_button.dynamicX) && button.has("x")) {
                double buttonC = button.getDouble("x");
                double ratio = buttonC / CallbackBridge.physicalWidth;
                n_button.dynamicX = ratio + " * ${screen_width}";
            }
            if (!Tools.isValidString(n_button.dynamicY) && button.has("y")) {
                double buttonC = button.getDouble("y");
                double ratio = buttonC / CallbackBridge.physicalHeight;
                n_button.dynamicY = ratio + " * ${screen_height}";
            }
            n_button.name = button.getString("name");
            n_button.opacity = ((float) ((button.getInt("transparency") - 100) * -1)) / 100f;
            n_button.passThruEnabled = button.getBoolean("passThruEnabled");
            n_button.isToggle = button.getBoolean("isToggle");
            n_button.setHeight(button.getInt("height"));
            n_button.setWidth(button.getInt("width"));
            n_button.bgColor = 0x4d000000;
            n_button.strokeWidth = 0;
            if (button.getBoolean("isRound")) {
                n_button.cornerRadius = 35f;
            }
            int next_idx = 0;
            if (button.getBoolean("holdShift")) {
                keycodes[next_idx] = LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT;
                next_idx++;
            }
            if (button.getBoolean("holdCtrl")) {
                keycodes[next_idx] = LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL;
                next_idx++;
            }
            if (button.getBoolean("holdAlt")) {
                keycodes[next_idx] = LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT;
                next_idx++;
            }
            keycodes[next_idx] = button.getInt("keycode");
            n_button.keycodes = keycodes;
            empty.mControlDataList.add(n_button);
        }
        empty.scaledAt = (float) oldLayoutJson.getDouble("scaledAt");
        empty.version = 3;
        return empty;
    }


    /**
     * Convert the layout stroke width to the V5 form
     */
    private static void convertStrokeWidth(CustomControls layout) {
        for (ControlData data : layout.mControlDataList) {
            data.strokeWidth = Tools.pxToDp(computeStrokeWidth(data.strokeWidth, data.getWidth(), data.getHeight()));
        }

        for (ControlDrawerData data : layout.mDrawerDataList) {
            data.properties.strokeWidth = Tools.pxToDp(computeStrokeWidth(data.properties.strokeWidth, data.properties.getWidth(), data.properties.getHeight()));
            for (ControlData subButtonData : data.buttonProperties) {
                subButtonData.strokeWidth = Tools.pxToDp(computeStrokeWidth(subButtonData.strokeWidth, data.properties.getWidth(), data.properties.getWidth()));
            }
        }
    }

    /**
     * Convert a size percentage into a px size, used by older layout versions
     */
    static int computeStrokeWidth(float widthInPercent, float width, float height) {
        float maxSize = Math.max(width, height);
        return (int) ((maxSize / 2) * (widthInPercent / 100));
    }
}
