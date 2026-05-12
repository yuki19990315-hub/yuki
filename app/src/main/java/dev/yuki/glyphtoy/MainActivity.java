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

    private GlyphMatrixBridge bridge;
    private TextView status;
    private TextView powerPolicy;
    private MatrixPreviewView previewView;
    private int[] selectedImageFrame = PixelMatrix.heart();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bridge = new GlyphMatrixBridge(this);
        int[] savedFrame = MatrixStorage.loadCustomFrame(this);
        if (savedFrame != null) {
            selectedImageFrame = savedFrame;
        }
        setContentView(buildContentView());
    }

    @Override
    protected void onStart() {
        super.onStart();
        bridge.init(new GlyphMatrixBridge.Listener() {
            @Override
            public void onConnected() {
                bridge.registerPhone4aPro();
                status.setText("Glyph Matrix SDK に接続しました。実機でハート表示を試せます。");
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
        previewView.setFrame(selectedImageFrame);
        root.addView(previewView, new LinearLayout.LayoutParams(-1, (int) (220 * getResources().getDisplayMetrics().density)));

        Button pickImage = new Button(this);
        pickImage.setText("画像を選んで13×13白黒化");
        pickImage.setOnClickListener(view -> pickImage());
        root.addView(pickImage, new LinearLayout.LayoutParams(-1, -2));

        Button preview = new Button(this);
        preview.setText("選んだ画像を背面に表示");
        preview.setOnClickListener(view -> {
            bridge.setAppFrame(selectedImageFrame);
            Toast.makeText(this, "13×13の白黒画像をGlyph Matrixに送信しました", Toast.LENGTH_SHORT).show();
        });
        root.addView(preview, new LinearLayout.LayoutParams(-1, -2));

        TextView policyTitle = new TextView(this);
        policyTitle.setText("背面ディスプレイの消灯設定");
        policyTitle.setTextSize(18);
        policyTitle.setPadding(0, padding, 0, padding / 4);
        root.addView(policyTitle, new LinearLayout.LayoutParams(-1, -2));

        powerPolicy = new TextView(this);
        powerPolicy.setTextSize(14);
        root.addView(powerPolicy, new LinearLayout.LayoutParams(-1, -2));
        refreshPowerPolicyText();

        Button duration = new Button(this);
        duration.setText("表示時間を切り替え");
        duration.setOnClickListener(view -> cycleDisplayDuration());
        root.addView(duration, new LinearLayout.LayoutParams(-1, -2));

        Button quietStart = new Button(this);
        quietStart.setText("夜間消灯の開始を+1時間");
        quietStart.setOnClickListener(view -> shiftQuietStart());
        root.addView(quietStart, new LinearLayout.LayoutParams(-1, -2));

        Button quietEnd = new Button(this);
        quietEnd.setText("夜間消灯の終了を+1時間");
        quietEnd.setOnClickListener(view -> shiftQuietEnd());
        root.addView(quietEnd, new LinearLayout.LayoutParams(-1, -2));

        Button manager = new Button(this);
        manager.setText("Glyph Toys 管理画面を開く");
        manager.setOnClickListener(view -> openGlyphToyManager());
        root.addView(manager, new LinearLayout.LayoutParams(-1, -2));

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
            selectedImageFrame = ImageMatrixConverter.fromBitmap(bitmap);
            MatrixStorage.saveCustomFrame(this, selectedImageFrame);
            previewView.setFrame(selectedImageFrame);
            MatrixWidgetProvider.updateAll(this);
            bridge.setAppFrame(selectedImageFrame);
            Toast.makeText(this, "画像を13×13の白黒表示に変換しました", Toast.LENGTH_SHORT).show();
        } catch (IOException exception) {
            Toast.makeText(this, "画像の読み込み中にエラーが発生しました", Toast.LENGTH_LONG).show();
        }
    }

    private void cycleDisplayDuration() {
        int current = MatrixStorage.loadDisplayDurationMinutes(this);
        int next;
        if (current == 1) {
            next = 5;
        } else if (current == 5) {
            next = 15;
        } else if (current == 15) {
            next = 60;
        } else if (current == 60) {
            next = 0;
        } else {
            next = 1;
        }
        MatrixStorage.saveDisplayDurationMinutes(this, next);
        refreshPowerPolicyText();
    }

    private void shiftQuietStart() {
        MatrixStorage.saveQuietHours(
                this,
                MatrixStorage.loadQuietStartHour(this) + 1,
                MatrixStorage.loadQuietEndHour(this)
        );
        refreshPowerPolicyText();
    }

    private void shiftQuietEnd() {
        MatrixStorage.saveQuietHours(
                this,
                MatrixStorage.loadQuietStartHour(this),
                MatrixStorage.loadQuietEndHour(this) + 1
        );
        refreshPowerPolicyText();
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
