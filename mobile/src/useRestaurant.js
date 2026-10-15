import { useMemo, useSyncExternalStore } from "react";
import { api, getState, subscribe } from "./api/store";
import { useSession } from "./session";

export function useRestaurant() {
  const { user } = useSession();
  const state = useSyncExternalStore(subscribe, getState, getState);
  return useMemo(() => {
    if (!user) {
      return null;
    }
    return {
      ...api.view(user),
      revision: state.seq,
      stats(period) {
        return api.stats(user.venueId, period);
      },
    };
  }, [state, user]);
}
