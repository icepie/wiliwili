package cn.xfangfang.wiliwili.player;

import android.net.Uri;
import android.util.Log;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@UnstableApi
final class CdnFailoverState {
    private final String kind;
    private final List<Uri> candidates;
    private int preferredIndex;

    CdnFailoverState(String kind, List<Uri> candidates) {
        this.kind = kind;
        this.candidates = candidates;
    }

    String getKind() {
        return kind;
    }

    List<Uri> getCandidates() {
        return candidates;
    }

    synchronized int getPreferredIndex() {
        if (candidates.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(preferredIndex, candidates.size() - 1));
    }

    synchronized void prefer(int index) {
        if (candidates.isEmpty()) {
            preferredIndex = 0;
            return;
        }
        preferredIndex = Math.max(0, Math.min(index, candidates.size() - 1));
    }
}

@UnstableApi
final class CdnFailoverDataSourceFactory implements DataSource.Factory {
    private final DataSource.Factory upstreamFactory;
    private final CdnFailoverState state;

    CdnFailoverDataSourceFactory(DataSource.Factory upstreamFactory, CdnFailoverState state) {
        this.upstreamFactory = upstreamFactory;
        this.state = state;
    }

    @Override
    public DataSource createDataSource() {
        return new CdnFailoverDataSource(upstreamFactory, state);
    }
}

@UnstableApi
final class CdnFailoverDataSource implements DataSource {
    private static final String TAG = "WiliwiliExoPlayer";

    private final DataSource.Factory upstreamFactory;
    private final CdnFailoverState state;
    private final ArrayList<TransferListener> transferListeners = new ArrayList<>(2);
    private DataSource upstream;

    CdnFailoverDataSource(DataSource.Factory upstreamFactory, CdnFailoverState state) {
        this.upstreamFactory = upstreamFactory;
        this.state = state;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        transferListeners.add(transferListener);
        if (upstream != null) {
            upstream.addTransferListener(transferListener);
        }
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        closeQuietly();
        List<Uri> candidates = state.getCandidates();
        if (candidates.isEmpty()) {
            throw new IOException("No CDN candidates for " + state.getKind());
        }

        int start = state.getPreferredIndex();
        IOException lastException = null;
        for (int attempt = 0; attempt < candidates.size(); attempt++) {
            int index = (start + attempt) % candidates.size();
            Uri uri = candidates.get(index);
            DataSource dataSource = upstreamFactory.createDataSource();
            for (TransferListener listener : transferListeners) {
                dataSource.addTransferListener(listener);
            }

            DataSpec candidateSpec = dataSpec.buildUpon().setUri(uri).build();
            try {
                long openedLength = dataSource.open(candidateSpec);
                upstream = dataSource;
                state.prefer(index);
                if (attempt > 0) {
                    Log.i(TAG, "CDN failover kind=" + state.getKind()
                            + " selected=" + index + " uri=" + ExoPlayerActivity.shortenForLog(String.valueOf(uri)));
                }
                return openedLength;
            } catch (IOException error) {
                closeQuietly(dataSource);
                lastException = error;
                Log.w(TAG, "CDN candidate failed kind=" + state.getKind()
                        + " index=" + index
                        + " uri=" + ExoPlayerActivity.shortenForLog(String.valueOf(uri))
                        + " cause=" + error.getClass().getSimpleName() + ": " + error.getMessage());
            }
        }

        throw lastException != null ? lastException
                : new IOException("Failed to open any CDN candidate for " + state.getKind());
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (upstream == null) {
            throw new IllegalStateException("read before open for " + state.getKind());
        }
        return upstream.read(buffer, offset, length);
    }

    @Override
    public Uri getUri() {
        return upstream == null ? null : upstream.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstream == null ? Collections.emptyMap() : upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        closeQuietly();
    }

    private void closeQuietly() {
        closeQuietly(upstream);
        upstream = null;
    }

    private static void closeQuietly(DataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        try {
            dataSource.close();
        } catch (IOException ignored) {
        }
    }
}
