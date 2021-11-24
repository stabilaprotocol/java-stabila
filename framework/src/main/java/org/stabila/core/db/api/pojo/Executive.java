package org.stabila.core.db.api.pojo;

import lombok.Data;

@Data(staticConstructor = "of")
public class Executive {

  private String address;
  private String publicKey;
  private String url;
  private boolean jobs;
}
