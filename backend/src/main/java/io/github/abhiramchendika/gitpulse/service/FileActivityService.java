package io.github.abhiramchendika.gitpulse.service;

import io.github.abhiramchendika.gitpulse.analysis.FileActivityAnalyzer;
import io.github.abhiramchendika.gitpulse.analysis.model.CommitFiles;
import io.github.abhiramchendika.gitpulse.analysis.model.CommitRecord;
import io.github.abhiramchendika.gitpulse.api.dto.FileActivityResponse;
import io.github.abhiramchendika.gitpulse.config.FileActivityProperties;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Which files change most, based on the most recent commits. This is the one feature whose cost
 * grows with the sample (one GitHub request per commit), so the sample is capped, lower without a
 * token, and details are cached by SHA.
 */
@Service
public class FileActivityService {

  private final RepositoryDataService data;
  private final FileActivityAnalyzer analyzer;
  private final AnalysisWindows windows;
  private final FileActivityProperties properties;

  public FileActivityService(
      RepositoryDataService data,
      FileActivityAnalyzer analyzer,
      AnalysisWindows windows,
      FileActivityProperties properties) {
    this.data = data;
    this.analyzer = analyzer;
    this.windows = windows;
    this.properties = properties;
  }

  /**
   * @param sample how many recent commits to analyse, or null for the default
   */
  public FileActivityResponse analyze(RepositoryRef ref, Integer sample) {
    boolean authenticated = data.authenticated();
    int limit = authenticated ? properties.maxSample() : properties.anonymousMaxSample();
    int requested = sample != null ? sample : authenticated ? properties.defaultSample() : limit;
    int size = Math.min(requested, limit);

    // The default one-year commit sample is usually cached already (the dashboard loads it).
    AnalysisWindows.Resolved window = windows.resolve(null, null);
    CommitSample commits = data.commits(ref, window.requestedSince(), window.requestedUntil());

    List<CommitRecord> candidates = commits.commits().stream().filter(c -> !c.merge()).toList();
    List<String> shas =
        candidates.stream()
            .sorted(Comparator.comparing(CommitRecord::committedAt).reversed())
            .limit(size)
            .map(CommitRecord::sha)
            .toList();

    // Calls go through the Spring proxy of RepositoryDataService, so each one is cached.
    List<CommitFiles> details =
        BoundedParallel.map(shas, properties.parallelRequests(), sha -> data.commitFiles(ref, sha));

    FileActivityResponse.Meta meta =
        new FileActivityResponse.Meta(
            window.now(),
            requested,
            limit,
            authenticated,
            candidates.size(),
            commits.commits().size() - candidates.size());
    return new FileActivityResponse(ref.fullName(), meta, analyzer.analyze(details));
  }
}
