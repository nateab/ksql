package io.confluent.ksql.engine;

import io.confluent.ksql.name.SourceName;
import io.confluent.ksql.query.QueryId;
import io.confluent.ksql.util.BinPackedPersistentQueryMetadataImpl;
import io.confluent.ksql.util.KsqlConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RuntimeAssignorTest {

  @Mock
  public BinPackedPersistentQueryMetadataImpl queryMetadata;
  private final QueryId query1 = new QueryId("Test1");
  private final QueryId query2 = new QueryId("Test2");
  private final Collection<SourceName> sources1 = Collections.singleton(SourceName.of("test1"));
  private final Collection<SourceName> sources2 = Collections.singleton(SourceName.of("test2"));

  private String firstRuntime;
  private RuntimeAssignor runtimeAssignor;
  private static final KsqlConfig KSQL_CONFIG = new KsqlConfig(
      Collections.singletonMap(KsqlConfig.KSQL_SHARED_RUNTIMES_COUNT, 1));

  @Before
  public void setUp() {
    runtimeAssignor = new RuntimeAssignor(KSQL_CONFIG);
    firstRuntime = runtimeAssignor.getRuntimeAndMaybeAddRuntime(
        query1,
        sources1,
        KSQL_CONFIG
    );
    when(queryMetadata.getQueryApplicationId()).thenReturn(firstRuntime);
    when(queryMetadata.getQueryId()).thenReturn(query1);
    when(queryMetadata.getSourceNames()).thenReturn(new HashSet<>(sources1));
  }

  @Test
  public void shouldCreateSandboxAndDroppingAQueryWillNotChangeReal() {
    final RuntimeAssignor sandbox = runtimeAssignor.createSandbox();
    sandbox.dropQuery(queryMetadata);
    assertThat("Was changed by sandbox.", runtimeAssignor.getIdToRuntime().containsKey(query1));
    assertThat("The query was not removed.", !sandbox.getIdToRuntime().containsKey(query1));
  }

  @Test
  public void shouldCreateSandboxAndAddingAQueryWillNotChangeReal() {
    final RuntimeAssignor sandbox = runtimeAssignor.createSandbox();
    sandbox.getRuntimeAndMaybeAddRuntime(
        query2,
        sources2,
        KSQL_CONFIG
    );
    assertThat("Was changed by sandbox.", !runtimeAssignor.getIdToRuntime().containsKey(query2));
    assertThat("The query was not added.", sandbox.getIdToRuntime().containsKey(query2));
    assertThat("Added a new Runtime.", sandbox.getRuntimesToSources().size() == 1);
  }

  @Test
  public void shouldCreateSandboxAndAddingAQueryAndRuntimeWillNotChangeReal() {
    final RuntimeAssignor sandbox = runtimeAssignor.createSandbox();
    sandbox.getRuntimeAndMaybeAddRuntime(
        query2,
        sources2,
        KSQL_CONFIG
    );
    assertThat("Was changed by sandbox.", !runtimeAssignor.getIdToRuntime().containsKey(query2));
    assertThat("The query was not added.", sandbox.getIdToRuntime().containsKey(query2));
    assertThat("Was changed by sandbox.", runtimeAssignor.getRuntimesToSources().size() == 1);
  }

  @Test
  public void shouldAddingAQueryWithDifferentSourcesWillNotAddARuntime() {
    runtimeAssignor.getRuntimeAndMaybeAddRuntime(
        query2,
        sources2,
        KSQL_CONFIG
    );
    assertThat("Query not added.", runtimeAssignor.getIdToRuntime().containsKey(query2));
    assertThat("Added a new Runtime.", runtimeAssignor.getRuntimesToSources().size() == 1);
  }

  @Test
  public void shouldAddingAQueryWithSameSourcesWillAddARuntime() {
    runtimeAssignor.getRuntimeAndMaybeAddRuntime(
        query2,
        sources1,
        KSQL_CONFIG
    );
    assertThat("Query not added.", runtimeAssignor.getIdToRuntime().containsKey(query2));
    assertThat("Added a new Runtime.", runtimeAssignor.getRuntimesToSources().size() == 2);
  }

  @Test
  public void shouldGetSameRuntimeForSameQueryId() {
    final String runtime = runtimeAssignor.getRuntimeAndMaybeAddRuntime(
        query1,
        sources1,
        KSQL_CONFIG
    );
    assertThat(runtime, equalTo(firstRuntime));
  }

  @Test
  public void shouldNotGetSameRuntimeForDifferentQueryIdAndSameSources() {
    final String runtime = runtimeAssignor.getRuntimeAndMaybeAddRuntime(
        query2,
        sources1,
        KSQL_CONFIG
    );
    assertThat(runtime, not(equalTo(firstRuntime)));
  }

  @Test
  public void shouldDropQueryThenUseSameRuntimeForTheSameSources() {
    runtimeAssignor.dropQuery(queryMetadata);
    final String runtime = runtimeAssignor.getRuntimeAndMaybeAddRuntime(
        query1,
        sources1,
        KSQL_CONFIG
    );
    assertThat(runtime, equalTo(firstRuntime));
  }

  @Test
  public void shouldRebuildAssignmentFromListOfQueries() {
    final RuntimeAssignor rebuilt = new RuntimeAssignor(KSQL_CONFIG);
    rebuilt.rebuildAssignment(Collections.singleton(queryMetadata));
    final String runtime = rebuilt.getRuntimeAndMaybeAddRuntime(
        query2,
        sources1,
        KSQL_CONFIG
    );
    assertThat(runtime, not(equalTo(firstRuntime)));
  }
}