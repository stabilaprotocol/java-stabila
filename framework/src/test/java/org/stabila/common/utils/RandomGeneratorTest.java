package org.stabila.common.utils;

import com.beust.jcommander.internal.Lists;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.junit.Ignore;
import org.junit.Test;
import org.stabila.core.capsule.ExecutiveCapsule;

@Slf4j
@Ignore
public class RandomGeneratorTest {

  @Test
  public void shuffle() {
    final List<ExecutiveCapsule> executiveCapsuleListBefore = this.getExecutiveList();
    logger.info("updateExecutiveSchedule,before: " + getExecutiveStringList(executiveCapsuleListBefore));
    final List<ExecutiveCapsule> executiveCapsuleListAfter = new RandomGenerator<ExecutiveCapsule>()
        .shuffle(executiveCapsuleListBefore, DateTime.now().getMillis());
    logger.info("updateExecutiveSchedule,after: " + getExecutiveStringList(executiveCapsuleListAfter));
  }

  private List<ExecutiveCapsule> getExecutiveList() {
    final List<ExecutiveCapsule> executiveCapsuleList = Lists.newArrayList();
    final ExecutiveCapsule executiveStabila = new ExecutiveCapsule(
        ByteString.copyFrom("00000000001".getBytes()), 0, "");
    final ExecutiveCapsule executiveOlivier = new ExecutiveCapsule(
        ByteString.copyFrom("00000000003".getBytes()), 100, "");
    final ExecutiveCapsule executiveVivider = new ExecutiveCapsule(
        ByteString.copyFrom("00000000005".getBytes()), 200, "");
    final ExecutiveCapsule executiveSenaLiu = new ExecutiveCapsule(
        ByteString.copyFrom("00000000006".getBytes()), 300, "");
    executiveCapsuleList.add(executiveStabila);
    executiveCapsuleList.add(executiveOlivier);
    executiveCapsuleList.add(executiveVivider);
    executiveCapsuleList.add(executiveSenaLiu);
    return executiveCapsuleList;
  }

  private List<String> getExecutiveStringList(List<ExecutiveCapsule> executiveStates) {
    return executiveStates.stream()
        .map(executiveCapsule -> ByteArray.toHexString(executiveCapsule.getAddress().toByteArray()))
        .collect(Collectors.toList());
  }
}