package org.stabila.core.vm.nativecontract.param;

import com.google.protobuf.ByteString;
import org.stabila.common.utils.StringUtil;
import org.stabila.protos.Protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Param used by VoteExecutiveProcessor
 */
public class VoteExecutiveParam {

  // Address of voter
  private byte[] voterAddress;

  // List of voter`s votes. Every entry contains executive address and vote count
  private final List<Protocol.Vote> votes = new ArrayList<>();

  public byte[] getVoterAddress() {
    return voterAddress;
  }

  public void setVoterAddress(byte[] voterAddress) {
    this.voterAddress = voterAddress;
  }

  public List<Protocol.Vote> getVotes() {
    return votes;
  }

  public void addVote(byte[] executiveAddress, long stabilaPower) {
    this.votes.add(Protocol.Vote.newBuilder()
        .setVoteAddress(ByteString.copyFrom(executiveAddress))
        .setVoteCount(stabilaPower)
        .build());
  }

  public String toJsonStr() {
    StringBuilder sb = new StringBuilder("{\"votes\":[");
    String template = "{\"vote_address\":\"%s\",\"vote_count\":%d}";
    for (Protocol.Vote vote : votes) {
      sb.append(String.format(template,
          StringUtil.encode58Check(vote.getVoteAddress().toByteArray()),
          vote.getVoteCount())).append(",");
    }
    if (!votes.isEmpty()) {
      sb.deleteCharAt(sb.length() - 1);
    }
    sb.append("]}");
    return sb.toString();
  }
}
