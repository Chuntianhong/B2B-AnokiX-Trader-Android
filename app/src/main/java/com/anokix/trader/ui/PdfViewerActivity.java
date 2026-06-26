package com.anokix.trader.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.trader.R;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-app PDF viewer. Renders each page of a local PDF file to a bitmap via the
 * system {@link PdfRenderer} (no external library) and shows them in a vertically
 * scrolling list. Toolbar carries the document title + a Share action; an
 * "Open in another app" fallback handles PDFs PdfRenderer can't decode.
 *
 * Launched with {@link #EXTRA_PATH} (absolute file path) + {@link #EXTRA_TITLE}.
 */
public class PdfViewerActivity extends AppCompatActivity {

    public static final String EXTRA_PATH = "pdf_path";
    public static final String EXTRA_TITLE = "pdf_title";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Bitmap> pages = new ArrayList<>();
    private PageAdapter adapter;
    private View loading;
    private View errorView;
    private File file;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(title != null && !title.isEmpty() ? title : "Document");
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_pdf_viewer);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_share) {
                share();
                return true;
            }
            return false;
        });

        loading = findViewById(R.id.loading);
        errorView = findViewById(R.id.errorView);
        findViewById(R.id.btnOpenExternal).setOnClickListener(v -> openExternally());

        RecyclerView list = findViewById(R.id.pdfPages);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PageAdapter();
        list.setAdapter(adapter);

        file = path != null ? new File(path) : null;
        if (file == null || !file.exists()) {
            showError();
            return;
        }
        renderPdf();
    }

    private void renderPdf() {
        loading.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        final int targetWidth = Math.min(getResources().getDisplayMetrics().widthPixels, 1440);
        io.execute(() -> {
            List<Bitmap> rendered = new ArrayList<>();
            try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer renderer = new PdfRenderer(pfd)) {
                int count = renderer.getPageCount();
                for (int i = 0; i < count; i++) {
                    PdfRenderer.Page page = renderer.openPage(i);
                    int w = targetWidth;
                    int h = (int) ((long) targetWidth * page.getHeight() / page.getWidth());
                    Bitmap bmp = Bitmap.createBitmap(w, Math.max(h, 1), Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(Color.WHITE);
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();
                    rendered.add(bmp);
                }
            } catch (Exception e) {
                runOnUiThread(this::showError);
                return;
            }
            runOnUiThread(() -> {
                loading.setVisibility(View.GONE);
                if (rendered.isEmpty()) {
                    showError();
                    return;
                }
                pages.clear();
                pages.addAll(rendered);
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void showError() {
        loading.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
    }

    private Uri fileUri() {
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
    }

    private void share() {
        if (file == null || !file.exists()) return;
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, fileUri());
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share GRV PDF"));
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't share PDF.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openExternally() {
        if (file == null || !file.exists()) return;
        try {
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(fileUri(), "application/pdf");
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(view);
        } catch (Exception e) {
            Toast.makeText(this, "No PDF viewer available.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
        for (Bitmap b : pages) {
            if (b != null && !b.isRecycled()) b.recycle();
        }
        pages.clear();
    }

    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pdf_page, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            h.image.setImageBitmap(pages.get(position));
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ImageView image;

            VH(@NonNull View v) {
                super(v);
                image = v.findViewById(R.id.pageImage);
            }
        }
    }
}
