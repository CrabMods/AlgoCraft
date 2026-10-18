package com.crabmods.algocraft.logic.repo;

import com.crabmods.algocraft.logic.Problem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Repository that syncs problems from the official GitHub repository.
 */
public class OfficialRepository implements ProblemRepository {
    private static final System.Logger LOGGER = System.getLogger(OfficialRepository.class.getName());
    private static final Gson GSON = new GsonBuilder().create();
    static final String DEFAULT_REPOSITORY_URL =
            "https://raw.githubusercontent.com/CrabMods/AlgoCraft/1.21.1/question_bank/official";
    
    private final Path cacheDir;
    private final AtomicReference<List<Problem>> problems = new AtomicReference<>(List.of());
    private final String repositoryUrl;
    private final RemoteFileClient remoteFileClient;
    private final boolean requireCompleteOfficialBank;
    private final TranslationLoader translationLoader;
    private final Object syncLock = new Object();
    private volatile boolean syncing = false;
    private volatile String lastError = null;
    
    public OfficialRepository(Path cacheDir) {
        this(cacheDir, DEFAULT_REPOSITORY_URL, new HttpRemoteFileClient(), true);
    }

    OfficialRepository(Path cacheDir, String repositoryUrl, RemoteFileClient remoteFileClient) {
        this(cacheDir, repositoryUrl, remoteFileClient, false);
    }

    OfficialRepository(
            Path cacheDir,
            String repositoryUrl,
            RemoteFileClient remoteFileClient,
            boolean requireCompleteOfficialBank
    ) {
        this(cacheDir, repositoryUrl, remoteFileClient, requireCompleteOfficialBank, ProblemTranslationManagerBridge::load);
    }

    OfficialRepository(
            Path cacheDir,
            String repositoryUrl,
            RemoteFileClient remoteFileClient,
            boolean requireCompleteOfficialBank,
            TranslationLoader translationLoader
    ) {
        this.cacheDir = cacheDir;
        this.repositoryUrl = repositoryUrl;
        this.remoteFileClient = remoteFileClient;
        this.requireCompleteOfficialBank = requireCompleteOfficialBank;
        this.translationLoader = translationLoader;
        
        try {
            Files.createDirectories(cacheDir);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Failed to create cache directory: " + cacheDir, e);
        }
    }
    
    @Override
    public String getName() {
        return "Official";
    }
    
    @Override
    public int getPriority() {
        return 100; // Highest priority
    }
    
    @Override
    public List<Problem> getProblems() {
        return new ArrayList<>(problems.get());
    }
    
    public boolean isSyncing() {
        return syncing;
    }
    
    public String getLastError() {
        return lastError;
    }
    
    @Override
    public CompletableFuture<Void> refresh() {
        // Normal refresh is offline-safe: it only reads the cached official repo.
        // Network updates are explicit via forceRefresh().
        return CompletableFuture.runAsync(() -> {
            loadFromCache();
            translationLoader.load(cacheDir);
        });
    }
    
    public CompletableFuture<Void> forceRefresh() {
        return CompletableFuture.runAsync(() -> {
            synchronized (syncLock) {
                syncing = true;
                lastError = null;

                try {
                    syncFromRemote();
                    loadFromCache(false);
                    translationLoader.load(cacheDir);
                } catch (CompletionException e) {
                    loadExistingCacheIfNoSnapshot(e);
                    lastError = "Sync failed: " + failureMessage(e);
                    LOGGER.log(System.Logger.Level.ERROR, "Failed to refresh official repository", e);
                    throw e;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    loadExistingCacheIfNoSnapshot(e);
                    lastError = "Sync failed: " + failureMessage(e);
                    LOGGER.log(System.Logger.Level.ERROR, "Failed to refresh official repository", e);
                    throw new CompletionException(e);
                } catch (Exception e) {
                    loadExistingCacheIfNoSnapshot(e);
                    lastError = "Sync failed: " + failureMessage(e);
                    LOGGER.log(System.Logger.Level.ERROR, "Failed to refresh official repository", e);
                    throw new CompletionException(e);
                } finally {
                    syncing = false;
                }
            }
        });
    }
    
    private void loadFromCache() {
        loadFromCache(true);
    }

    private void loadFromCache(boolean validateCompleteOfficialBank) {
        List<Problem> loaded = new ArrayList<>();
        
        if (!Files.exists(cacheDir)) {
            if (problems.get().isEmpty()) {
                problems.set(List.of());
                return;
            }
            throw new CompletionException(new IOException("Official cache directory is missing: " + cacheDir));
        }
        if (!Files.isDirectory(cacheDir)) {
            throw new CompletionException(new IOException("Official cache path is not a directory: " + cacheDir));
        }
        if (validateCompleteOfficialBank && requireCompleteOfficialBank && !isCacheDirectoryEmpty()) {
            try {
                RemoteRepositoryDownloader.validateCachedOfficialRepository(cacheDir);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }

        try (Stream<Path> files = Files.list(cacheDir)) {
            List<Path> problemFiles = files
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> p.getFileName().toString().matches("p\\d+\\.json"))
                    .sorted(Comparator.comparingInt(OfficialRepository::problemNumber))
                    .toList();
            for (Path file : problemFiles) {
                loaded.add(loadProblem(file));
            }
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Failed to load official cache from " + cacheDir, e);
            throw new CompletionException(e);
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Failed to load official cache from " + cacheDir, e);
            throw e;
        }

        problems.set(List.copyOf(loaded));
        LOGGER.log(System.Logger.Level.DEBUG, "Loaded " + loaded.size() + " problems from cache");
    }
    
    private Problem loadProblem(Path file) throws IOException {
        try {
            String json = Files.readString(file);
            Problem problem = GSON.fromJson(json, Problem.class);
            if (problem == null || !problem.isValid()) {
                throw new IOException("Invalid official cached problem JSON: " + file);
            }
            problem.setAssetBaseDir(cacheDir);
            return problem;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to load official cached problem from " + file, e);
        }
    }

    private void syncFromRemote() throws IOException, InterruptedException {
        if (requireCompleteOfficialBank) {
            RemoteRepositoryDownloader.updateOfficial(repositoryUrl, cacheDir, remoteFileClient);
        } else {
            RemoteRepositoryDownloader.update(repositoryUrl, cacheDir, remoteFileClient);
        }
        LOGGER.log(System.Logger.Level.INFO, "Updated official repository cache at " + cacheDir);
    }

    private static String failureMessage(Throwable throwable) {
        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
        String message = cause.getMessage();
        return message != null ? message : cause.toString();
    }

    private static int problemNumber(Path path) {
        String name = path.getFileName().toString();
        if (name.matches("p\\d+\\.json")) {
            return Integer.parseInt(name.substring(1, name.length() - 5));
        }
        return Integer.MAX_VALUE;
    }

    private void loadExistingCacheIfNoSnapshot(Throwable originalFailure) {
        if (!problems.get().isEmpty()) {
            return;
        }
        try {
            loadFromCache();
            translationLoader.load(cacheDir);
        } catch (RuntimeException fallbackFailure) {
            originalFailure.addSuppressed(fallbackFailure);
        }
    }

    private boolean isCacheDirectoryEmpty() {
        try (Stream<Path> entries = Files.list(cacheDir)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    private static class ProblemTranslationManagerBridge {
        static void load(Path cacheDir) {
            try {
                com.crabmods.algocraft.logic.ProblemTranslationManager.loadAllTranslations(cacheDir).join();
            } catch (CompletionException e) {
                LOGGER.log(System.Logger.Level.ERROR, "Failed to load official translations from " + cacheDir, e.getCause());
                throw e;
            } catch (Throwable e) {
                LOGGER.log(System.Logger.Level.ERROR, "Failed to load official translations from " + cacheDir, e);
                throw new CompletionException(e);
            }
        }
    }

    @FunctionalInterface
    interface TranslationLoader {
        void load(Path cacheDir);
    }
}
