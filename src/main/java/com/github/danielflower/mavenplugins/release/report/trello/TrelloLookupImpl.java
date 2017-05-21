package com.github.danielflower.mavenplugins.release.report.trello;

import com.julienvey.trello.Trello;
import com.julienvey.trello.TrelloHttpClient;
import com.julienvey.trello.domain.Card;
import com.julienvey.trello.impl.TrelloImpl;
import com.julienvey.trello.impl.http.ApacheHttpClient;
import org.apache.commons.lang3.text.StrLookup;

/**
 * @author Ondrej.Bozek@clevermaps.cz
 **/
public class TrelloLookupImpl extends StrLookup<String> {

    private TrelloAuth trelloAuth;

    public TrelloLookupImpl(TrelloAuth trelloAuth) {
        this.trelloAuth = trelloAuth;
    }

    @Override
    public String lookup(String key) {
        String title = key;
        TrelloHttpClient client = new ApacheHttpClient();
        Trello trelloApi = new TrelloImpl(trelloAuth.getKey(), trelloAuth.getToken(), client);
        Card card = trelloApi.getCard(key);
        title = card.getName();
        String url = card.getShortUrl();
        return "[" + title + "](" + url + ")";
    }
}
