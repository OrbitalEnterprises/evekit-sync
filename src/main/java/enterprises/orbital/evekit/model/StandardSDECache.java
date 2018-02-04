package enterprises.orbital.evekit.model;

import enterprises.orbital.evekit.sde.client.api.InventoryApi;
import enterprises.orbital.evekit.sde.client.invoker.ApiException;
import enterprises.orbital.evekit.sde.client.model.InvCategory;
import enterprises.orbital.evekit.sde.client.model.InvGroup;
import enterprises.orbital.evekit.sde.client.model.InvType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A cache of common information needed to improve certain synchronization activities (like asset locations).
 */
public class StandardSDECache implements SDECache {

  private Map<Integer, InvType> inventoryTypeCache = new HashMap<>();
  private Map<Integer, InvGroup> inventoryGroupCache = new HashMap<>();
  private Map<Integer, InvCategory> inventoryCategoryCache = new HashMap<>();

  public InvType getType(int typeID) throws ApiException {
    synchronized (inventoryTypeCache) {
      if (inventoryTypeCache.containsKey(typeID))
        return inventoryTypeCache.get(typeID);

      InventoryApi api = new InventoryApi();
      List<InvType> queryResults = api.getTypes(null, null, "{values:[" + typeID + "]}",
                                                null, null, null, null, null,
                                                null, null, null, null, null, null,
                                                null, null, null, null);
      InvType result = queryResults.isEmpty() ? null : queryResults.get(0);
      inventoryTypeCache.put(typeID, result);
      return result;
    }
  }

  @SuppressWarnings("Duplicates")
  public InvGroup getGroup(int groupID) throws ApiException {
    synchronized (inventoryGroupCache) {
      if (inventoryGroupCache.containsKey(groupID))
        return inventoryGroupCache.get(groupID);

      InventoryApi api = new InventoryApi();
      List<InvGroup> queryResults = api.getGroups(null, null, "{values:[" + groupID + "]}",
                                                  null,null,null,null,null,
                                                  null,null,null);
      InvGroup result = queryResults.isEmpty() ? null : queryResults.get(0);
      inventoryGroupCache.put(groupID, result);
      return result;
    }
  }

  public InvCategory getCategory(int categoryID) throws ApiException {
    synchronized (inventoryCategoryCache) {
      if (inventoryCategoryCache.containsKey(categoryID))
        return inventoryCategoryCache.get(categoryID);

      InventoryApi api = new InventoryApi();
      List<InvCategory> queryResults = api.getCategories(null, null, "{values:[" + categoryID + "]}",
                                                  null,null,null);
      InvCategory result = queryResults.isEmpty() ? null : queryResults.get(0);
      inventoryCategoryCache.put(categoryID, result);
      return result;
    }
  }

}
