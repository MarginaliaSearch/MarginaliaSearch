package nu.marginalia.control.sys.model;

import nu.marginalia.storage.model.FileStorage;

import java.util.List;

/** A process that has been manually aborted by a user,
 * ... or error?
 */
public record AbortedProcess(String name,
                             long msgId,
                             String startDateTime,
                             String stopDateTime,
                             List<FileStorage> associatedStorages)
{

}
