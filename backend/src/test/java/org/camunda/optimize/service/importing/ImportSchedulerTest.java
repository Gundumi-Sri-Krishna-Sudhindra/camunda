package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.util.ConfigurationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/applicationContext.xml"})
public class ImportSchedulerTest {

  @Autowired
  private ImportServiceProvider importServiceProvider;

  @Autowired
  private ImportScheduler importScheduler;

  @Autowired
  private ConfigurationService configurationService;

  @Before
  public void setUp() {
    importScheduler.resetBackoffCounter();
    importScheduler.importScheduleJobs.clear();
  }

  @Test
  public void allImportsAreTriggered() throws InterruptedException {

    // given
    List<ImportService> services = mockImportServices();
    when(importServiceProvider.getServices()).thenReturn(services);
    importScheduler.scheduleProcessEngineImport();

    // when
    importScheduler.executeJob();

    // then
    for (ImportService service : services) {
      verify(service, times(1)).executeImport();
    }
  }

  private List<ImportService> mockImportServices() {
    List<ImportService> services = new ArrayList<>();
    services.add(mock(ActivityImportService.class));
    services.add(mock(ProcessDefinitionImportService.class));
    services.add(mock(ProcessDefinitionXmlImportService.class));
    return services;
  }

  @Test
  public void testNotBackingOffIfImportPagesFound() throws Exception {
    //given
    List<ImportService> services = mockImportServices();
    when(importServiceProvider.getServices()).thenReturn(services);
    when(services.get(0).executeImport()).thenReturn(1);
    importScheduler.scheduleProcessEngineImport();

    //when
    importScheduler.executeJob();

    assertThat(importScheduler.getBackoffCounter(), is(1L));
  }

  @Test
  public void testBackingOffIfNoImportPagesFound() throws Exception {
    //given
    List<ImportService> services = mockImportServices();
    when(importServiceProvider.getServices()).thenReturn(services);
    when(services.get(0).executeImport()).thenReturn(0);
    importScheduler.scheduleProcessEngineImport();

    //when
    importScheduler.executeJob();

    assertThat(importScheduler.getBackoffCounter(), is(1L));
  }

  @Test
  public void testBackoffIncreaseWithoutJobs() throws Exception {
    assertThat(importScheduler.getBackoffCounter(), is(0L));

    //when
    importScheduler.executeJob();

    //then
    assertThat(importScheduler.getBackoffCounter(), is(1L));
  }

  @Test
  public void testBackoffResetAfterPage() {
    //given
    assertThat(importScheduler.getBackoffCounter(), is(0L));
    importScheduler.executeJob();
    assertThat(importScheduler.getBackoffCounter(), is(1L));
    importScheduler.executeJob();
    assertThat(importScheduler.getBackoffCounter(), is(2L));
    List<ImportService> services = mockImportServices();
    when(importServiceProvider.getServices()).thenReturn(services);
    when(services.get(0).executeImport()).thenReturn(1);

    //when
    importScheduler.executeJob();

    //then
    assertThat(importScheduler.getBackoffCounter(), is(1L));
  }

  @Test
  public void testBackoffNotExceedingMax() throws Exception {
    assertThat(importScheduler.calculateBackoff(0), is(1L));
    assertThat(importScheduler.calculateBackoff(1), is(1L));
    //does not increase after 2
    importScheduler.executeJob();
    assertThat(importScheduler.calculateBackoff(0), is(2L));
    importScheduler.executeJob();
    assertThat(importScheduler.calculateBackoff(0), is(3L));
    importScheduler.executeJob();
    assertThat(importScheduler.calculateBackoff(0), is(3L));
  }

}
