package enterprises.orbital.evekit.model;

import enterprises.orbital.evekit.sde.client.invoker.ApiException;
import enterprises.orbital.evekit.sde.client.model.InvCategory;
import enterprises.orbital.evekit.sde.client.model.InvGroup;
import enterprises.orbital.evekit.sde.client.model.InvType;

public interface SDECache {
  InvType getType(int typeID) throws ApiException;
  InvGroup getGroup(int groupID) throws ApiException;
  InvCategory getCategory(int categoryID) throws ApiException;
}
