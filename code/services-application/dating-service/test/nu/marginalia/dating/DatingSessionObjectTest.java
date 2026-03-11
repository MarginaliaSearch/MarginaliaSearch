package nu.marginalia.dating;

import nu.marginalia.browse.DbBrowseDomainsRandom;
import nu.marginalia.browse.model.BrowseResult;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.*;

import java.net.URISyntaxException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for the DatingSessionObject state machine. */
public class DatingSessionObjectTest {

    DatingSessionObject session;
    DbBrowseDomainsRandom browseRandom;
    DomainBlacklist blacklist;

    @BeforeEach
    void setup() {
        session = new DatingSessionObject();
        browseRandom = mock(DbBrowseDomainsRandom.class);
        blacklist = mock(DomainBlacklist.class);
    }

    private BrowseResult makeBrowseResult(int id) {
        try {
            return new BrowseResult(new EdgeUrl("http://example" + id + ".com/"), id, 1.0, true);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testInitialState_currentIsNull() {
        assertNull(session.getCurrent());
        assertFalse(session.hasHistory());
    }

    @Test
    void testNext_populatesFromRandom() {
        var results = List.of(makeBrowseResult(1), makeBrowseResult(2), makeBrowseResult(3));
        when(browseRandom.getRandomDomains(any(Integer.class), any(), any(Integer.class)))
                .thenReturn(results);

        BrowseResult result = session.next(browseRandom, blacklist);
        assertNotNull(result);
        assertEquals(1, result.domainId());
    }

    @Test
    void testBrowseForward_setsCurrent() {
        var res = makeBrowseResult(1);
        session.browseForward(res);
        assertEquals(res, session.getCurrent());
    }

    @Test
    void testBrowseForward_pushesPreviousToHistory() {
        var res1 = makeBrowseResult(1);
        var res2 = makeBrowseResult(2);

        session.browseForward(res1);
        assertFalse(session.hasHistory());

        session.browseForward(res2);
        assertTrue(session.hasHistory());
        assertEquals(res2, session.getCurrent());
    }

    @Test
    void testTakeFromHistory_returnsLastBrowsed() {
        var res1 = makeBrowseResult(1);
        var res2 = makeBrowseResult(2);

        session.browseForward(res1);
        session.browseForward(res2);

        BrowseResult taken = session.takeFromHistory();
        assertNotNull(taken);
        assertEquals(res1, taken);
    }

    @Test
    void testTakeFromHistory_emptyReturnsNull() {
        assertNull(session.takeFromHistory());
    }

    @Test
    void testBrowseBackward_pushesCurrentToQueue() {
        var res1 = makeBrowseResult(1);
        var res2 = makeBrowseResult(2);

        session.browseForward(res1);
        session.browseBackward(res2);

        assertEquals(res2, session.getCurrent());
        // res1 should be in the queue now
        assertFalse(session.queue.isEmpty());
    }

    @Test
    void testIsRecent_detectsCurrentAndHistory() {
        var res1 = makeBrowseResult(1);
        var res2 = makeBrowseResult(2);
        var res3 = makeBrowseResult(3);

        session.browseForward(res1);
        session.browseForward(res2);

        assertTrue(session.isRecent(res2)); // current
        assertTrue(session.isRecent(res1)); // in history
        assertFalse(session.isRecent(res3)); // not seen
    }

    @Test
    void testResetQueue_clearsQueue() {
        var results = List.of(makeBrowseResult(1), makeBrowseResult(2));
        when(browseRandom.getRandomDomains(any(Integer.class), any(), any(Integer.class)))
                .thenReturn(results);

        session.next(browseRandom, blacklist); // populates queue
        assertFalse(session.queue.isEmpty());

        session.resetQueue();
        assertTrue(session.queue.isEmpty());
    }
}
