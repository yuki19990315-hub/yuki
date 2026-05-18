package dev.yuki.glyphtoy;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;

public final class MainActivity extends Activity {
    private static final int REQUEST_PICK_IMAGE = 42;
    private static final int MAX_GIF_FRAMES = 24;

    private GlyphMatrixBridge bridge;
    private TextView status;
    private TextView powerPolicy;
    private TextView gifFrameStatus;
    private Button onionSkinButton;
    private MatrixPreviewView previewView;
    private int[][] gifFrames;
    private int selectedGifFrameIndex;
    private int[] selectedImageFrame = PixelMatrix.heart();
    private boolean showOnionSkin = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bridge = new GlyphMatrixBridge(this);
        loadSavedGifFrames();
        setContentView(buildContentView());
    }

    @Override
    protected void onStart() {
        super.onStart();
        bridge.init(new GlyphMatrixBridge.Listener() {
            @Override
            public void onConnected() {
                bridge.registerPhone4aPro();
                status.setText("Glyph Matrix SDK に接続しました。実機で選択中のコマ表示を試せます。");
            }

            @Override
            public void onDisconnected() {
                status.setText("Glyph Matrix SDK から切断されました。");
            }

            @Override
            public void onError(String message, Throwable throwable) {
                status.setText(message + "\n" + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            }
        });
    }

    @Override
    protected void onStop() {
        bridge.closeAppMatrix();
        bridge.unInit();
        super.onStop();
    }

    private View buildContentView() {
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Yuki Glyph Toy");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView body = new TextView(this);
        body.setText("Nothing Phone (4a) Pro 向けの最小 AOD Glyph Toy です。端末が届いたら公式 SDK の AAR を app/libs に置いて、設定画面で Toy を有効化してください。");
        body.setTextSize(16);
        body.setPadding(0, padding, 0, padding / 2);
        root.addView(body, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("SDK 接続待ち...");
        status.setTextSize(14);
        status.setPadding(0, 0, 0, padding);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        previewView = new MatrixPreviewView(this);
        root.addView(previewView, new LinearLayout.LayoutParams(-1, (int) (220 * getResources().getDisplayMetrics().density)));

        TextView gifTitle = new TextView(this);
        gifTitle.setText("GIFコマ");
        gifTitle.setTextSize(18);
        gifTitle.setPadding(0, padding / 2, 0, padding / 4);
        root.addView(gifTitle, new LinearLayout.LayoutParams(-1, -2));

        gifFrameStatus = new TextView(this);
        gifFrameStatus.setTextSize(14);
        gifFrameStatus.setPadding(0, 0, 0, padding / 4);
        root.addView(gifFrameStatus, new LinearLayout.LayoutParams(-1, -2));

        addButton(root, "前のコマ", view -> selectGifFrame(selectedGifFrameIndex - 1));
        addButton(root, "次のコマ", view -> selectGifFrame(selectedGifFrameIndex + 1));
        addButton(root, "コマを追加", view -> addGifFrame());
        addButton(root, "コマを削除", view -> deleteGifFrame());
        onionSkinButton = addButton(root, "前コマ下敷き: ON", view -> toggleOnionSkin());

        addButton(root, "現在のコマに画像を読み込む", view -> pickImage());

        addButton(root, "選択中のコマを背面に表示", view -> {
            bridge.setAppFrame(selectedImageFrame);
            Toast.makeText(this, "選択中の13×13コマをGlyph Matrixに送信しました", Toast.LENGTH_SHORT).show();
        });

        TextView policyTitle = new TextView(this);
        policyTitle.setText("背面ディスプレイの表示設定");
        policyTitle.setTextSize(18);
        policyTitle.setPadding(0, padding, 0, padding / 4);
        root.addView(policyTitle, new LinearLayout.LayoutParams(-1, -2));

        powerPolicy = new TextView(this);
        powerPolicy.setTextSize(14);
        root.addView(powerPolicy, new LinearLayout.LayoutParams(-1, -2));
        refreshPowerPolicyText();

        addButton(root, "Glyph Toys 管理画面を開く", view -> openGlyphToyManager());

        refreshGifEditor();
        return scrollView;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        loadImage(data.getData());
    }

    private void loadSavedGifFrames() {
        int[][] savedGifFrames = MatrixStorage.loadGifFrames(this);
        if (savedGifFrames != null) {
            gifFrames = savedGifFrames;
            selectedGifFrameIndex = clampIndex(MatrixStorage.loadSelectedGifFrameIndex(this), gifFrames.length);
            selectedImageFrame = gifFrames[selectedGifFrameIndex].clone();
            return;
        }

        int[] savedFrame = MatrixStorage.loadCustomFrame(this);
        selectedImageFrame = savedFrame == null ? PixelMatrix.heart() : savedFrame;
        gifFrames = new int[][]{selectedImageFrame.clone()};
        selectedGifFrameIndex = 0;
    }

    private Button addButton(LinearLayout root, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        root.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    private void loadImage(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                Toast.makeText(this, "画像を開けませんでした", Toast.LENGTH_LONG).show();
                return;
            }
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                Toast.makeText(this, "画像を読み込めませんでした", Toast.LENGTH_LONG).show();
                return;
            }
            replaceSelectedGifFrame(ImageMatrixConverter.fromBitmap(bitmap));
            bridge.setAppFrame(selectedImageFrame);
            Toast.makeText(this, "現在のGIFコマを13×13の白黒表示に変換しました", Toast.LENGTH_SHORT).show();
        } catch (IOException exception) {
            Toast.makeText(this, "画像の読み込み中にエラーが発生しました", Toast.LENGTH_LONG).show();
        }
    }

    private void selectGifFrame(int index) {
        ensureGifFrames();
        int length = gifFrames.length;
        selectedGifFrameIndex = ((index % length) + length) % length;
        selectedImageFrame = gifFrames[selectedGifFrameIndex].clone();
        MatrixStorage.saveSelectedGifFrameIndex(this, selectedGifFrameIndex);
        refreshGifEditor();
    }

    private void addGifFrame() {
        ensureGifFrames();
        if (gifFrames.length >= MAX_GIF_FRAMES) {
            Toast.makeText(this, "GIFコマは最大" + MAX_GIF_FRAMES + "枚までです", Toast.LENGTH_SHORT).show();
            return;
        }

        int insertIndex = selectedGifFrameIndex + 1;
        int[][] next = new int[gifFrames.length + 1][];
        for (int i = 0; i < insertIndex; i++) {
            next[i] = gifFrames[i].clone();
        }
        next[insertIndex] = selectedImageFrame.clone();
        for (int i = insertIndex; i < gifFrames.length; i++) {
            next[i + 1] = gifFrames[i].clone();
        }
        gifFrames = next;
        selectedGifFrameIndex = insertIndex;
        selectedImageFrame = gifFrames[selectedGifFrameIndex].clone();
        persistGifFrames();
        refreshGifEditor();
    }

    private void deleteGifFrame() {
        ensureGifFrames();
        if (gifFrames.length <= 1) {
            Toast.makeText(this, "最後の1コマは残します", Toast.LENGTH_SHORT).show();
            return;
        }

        int[][] next = new int[gifFrames.length - 1][];
        int target = 0;
        for (int i = 0; i < gifFrames.length; i++) {
            if (i != selectedGifFrameIndex) {
                next[target] = gifFrames[i].clone();
                target++;
            }
        }
        gifFrames = next;
        selectedGifFrameIndex = clampIndex(selectedGifFrameIndex, gifFrames.length);
        selectedImageFrame = gifFrames[selectedGifFrameIndex].clone();
        persistGifFrames();
        refreshGifEditor();
    }

    private void toggleOnionSkin() {
        showOnionSkin = !showOnionSkin;
        refreshGifEditor();
    }

    private void replaceSelectedGifFrame(int[] frame) {
        ensureGifFrames();
        selectedImageFrame = frame.clone();
        gifFrames[selectedGifFrameIndex] = selectedImageFrame.clone();
        persistGifFrames();
        refreshGifEditor();
    }

    private void refreshGifEditor() {
        ensureGifFrames();
        selectedImageFrame = gifFrames[selectedGifFrameIndex].clone();
        if (previewView != null) {
            previewView.setFrames(selectedImageFrame, showOnionSkin ? previousGifFrame() : null);
        }
        if (gifFrameStatus != null) {
            String overlay = showOnionSkin && gifFrames.length > 1 ? " / 前コマを薄く表示" : "";
            gifFrameStatus.setText("GIFコマ " + (selectedGifFrameIndex + 1) + " / " + gifFrames.length + overlay);
        }
        if (onionSkinButton != null) {
            onionSkinButton.setText("前コマ下敷き: " + (showOnionSkin ? "ON" : "OFF"));
        }
    }

    private int[] previousGifFrame() {
        if (gifFrames.length < 2) {
            return null;
        }
        int previousIndex = selectedGifFrameIndex == 0 ? gifFrames.length - 1 : selectedGifFrameIndex - 1;
        return gifFrames[previousIndex];
    }

    private void persistGifFrames() {
        MatrixStorage.saveGifFrames(this, gifFrames);
        MatrixStorage.saveSelectedGifFrameIndex(this, selectedGifFrameIndex);
        MatrixStorage.saveCustomFrame(this, selectedImageFrame);
        MatrixWidgetProvider.updateAll(this);
    }

    private void ensureGifFrames() {
        if (gifFrames == null || gifFrames.length == 0) {
            gifFrames = new int[][]{selectedImageFrame.clone()};
            selectedGifFrameIndex = 0;
        }
        selectedGifFrameIndex = clampIndex(selectedGifFrameIndex, gifFrames.length);
    }

    private static int clampIndex(int index, int length) {
        if (length <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(index, length - 1));
    }

    private void refreshPowerPolicyText() {
        if (powerPolicy != null) {
            powerPolicy.setText(GlyphDisplayPolicy.describe(this));
        }
    }

    private void openGlyphToyManager() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                "com.nothing.thirdparty",
                "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"
        ));
        try {
            startActivity(intent);
        } catch (RuntimeException exception) {
            Toast.makeText(this, "Nothing OS の Glyph Toys 管理画面を開けませんでした", Toast.LENGTH_LONG).show();
        }
    }
}
