package ca.ubc.cs.cs317.dnslookup;

import java.io.Console;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.*;

public class DNSLookupService {

    private static final int DEFAULT_DNS_PORT = 53;
    private static final int MAX_INDIRECTION_LEVEL = 10;

    private static int currIndirectionLevel = 0;
    private static InetAddress rootServer;
    private static boolean verboseTracing = false;
    private static DatagramSocket socket;
    private static boolean querySuccess = true;
    private static int previousQueryID = -1;
    private static RecordType currOriginalQueryType;

    private static DNSCache cache = DNSCache.getInstance();

    private static Random random = new Random();

    /**
     * Main function, called when program is first invoked.
     *
     * @param args list of arguments specified in the command line.
     */
    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Invalid call. Usage:");
            System.err.println("\tjava -jar DNSLookupService.jar rootServer");
            System.err.println(
                    "where rootServer is the IP address (in dotted form) of the root DNS server to start the search at.");
            System.exit(1);
        }

        try {
            rootServer = InetAddress.getByName(args[0]);
            System.out.println("Root DNS server is: " + rootServer.getHostAddress());
        } catch (UnknownHostException e) {
            System.err.println("Invalid root server (" + e.getMessage() + ").");
            System.exit(1);
        }

        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(5000);
        } catch (SocketException ex) {
            ex.printStackTrace();
            System.exit(1);
        }

        Scanner in = new Scanner(System.in);
        Console console = System.console();
        do {
            // Use console if one is available, or standard input if not.
            String commandLine;
            if (console != null) {
                System.out.print("DNSLOOKUP> ");
                commandLine = console.readLine();
            } else
                try {
                    commandLine = in.nextLine();
                } catch (NoSuchElementException ex) {
                    break;
                }
            // If reached end-of-file, leave
            if (commandLine == null)
                break;

            // Ignore leading/trailing spaces and anything beyond a comment character
            commandLine = commandLine.trim().split("#", 2)[0];

            // If no command shown, skip to next command
            if (commandLine.trim().isEmpty())
                continue;

            String[] commandArgs = commandLine.split(" ");

            if (commandArgs[0].equalsIgnoreCase("quit") || commandArgs[0].equalsIgnoreCase("exit"))
                break;
            else if (commandArgs[0].equalsIgnoreCase("server")) {
                // SERVER: Change root nameserver
                if (commandArgs.length == 2) {
                    try {
                        rootServer = InetAddress.getByName(commandArgs[1]);
                        System.out.println("Root DNS server is now: " + rootServer.getHostAddress());
                    } catch (UnknownHostException e) {
                        System.out.println("Invalid root server (" + e.getMessage() + ").");
                        continue;
                    }
                } else {
                    System.out.println("Invalid call. Format:\n\tserver IP");
                    continue;
                }
            } else if (commandArgs[0].equalsIgnoreCase("trace")) {
                // TRACE: Turn trace setting on or off
                if (commandArgs.length == 2) {
                    if (commandArgs[1].equalsIgnoreCase("on"))
                        verboseTracing = true;
                    else if (commandArgs[1].equalsIgnoreCase("off"))
                        verboseTracing = false;
                    else {
                        System.err.println("Invalid call. Format:\n\ttrace on|off");
                        continue;
                    }
                    System.out.println("Verbose tracing is now: " + (verboseTracing ? "ON" : "OFF"));
                } else {
                    System.err.println("Invalid call. Format:\n\ttrace on|off");
                    continue;
                }
            } else if (commandArgs[0].equalsIgnoreCase("lookup") || commandArgs[0].equalsIgnoreCase("l")) {
                // LOOKUP: Find and print all results associated to a name.
                RecordType type;
                if (commandArgs.length == 2)
                    type = RecordType.A;
                else if (commandArgs.length == 3)
                    try {
                        type = RecordType.valueOf(commandArgs[2].toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        System.err.println("Invalid query type. Must be one of:\n\tA, AAAA, NS, MX, CNAME");
                        continue;
                    }
                else {
                    System.err.println("Invalid call. Format:\n\tlookup hostName [type]");
                    continue;
                }
                findAndPrintResults(commandArgs[1], type);
            } else if (commandArgs[0].equalsIgnoreCase("dump")) {
                // DUMP: Print all results still cached
                cache.forEachNode(DNSLookupService::printResults);
            } else {
                System.err.println("Invalid command. Valid commands are:");
                System.err.println("\tlookup fqdn [type]");
                System.err.println("\ttrace on|off");
                System.err.println("\tserver IP");
                System.err.println("\tdump");
                System.err.println("\tquit");
                continue;
            }

        } while (true);

        socket.close();
        System.out.println("Goodbye!");
    }

    /**
     * Finds all results for a host name and type and prints them on the standard
     * output.
     *
     * @param hostName Fully qualified domain name of the host being searched.
     * @param type     Record type for search.
     */
    private static void findAndPrintResults(String hostName, RecordType type) {

        DNSNode node = new DNSNode(hostName, type);
        currOriginalQueryType = type;
        printResults(node, getResults(node, 0));
    }

    /**
     * Finds all the result for a specific node.
     *
     * @param node             Host and record type to be used for search.
     * @param indirectionLevel Control to limit the number of recursive calls due to
     *                         CNAME redirection. The initial call should be made
     *                         with 0 (zero), while recursive calls for regarding
     *                         CNAME results should increment this value by 1. Once
     *                         this value reaches MAX_INDIRECTION_LEVEL, the
     *                         function prints an error message and returns an empty
     *                         set.
     * @return A set of resource records corresponding to the specific query
     *         requested.
     */
    private static Set<ResourceRecord> getResults(DNSNode node, int indirectionLevel) {
        currOriginalQueryType = node.getType();
        if (indirectionLevel > MAX_INDIRECTION_LEVEL) {
            System.err.println("Maximum number of indirection levels reached.");
            return Collections.emptySet();
        }

        if (cache.getCachedResults(node).isEmpty()) {
            retrieveResultsFromServer(node, rootServer);
            // if retrieve got A or AAAA, do nothing
            if (!cache.getCachedResults(node).isEmpty()) {
                // do nothing
            } else {
                // check for CNAME
                DNSNode cNameNode = new DNSNode(node.getHostName(), RecordType.CNAME);
                List<ResourceRecord> cNameResults = new ArrayList<ResourceRecord>(cache.getCachedResults(cNameNode));
                if (!cNameResults.isEmpty()) {
                    ResourceRecord cNameResult = cNameResults.get(0);
                    // got a CNAME for the node
                    DNSNode newNode = new DNSNode(cNameResult.getTextResult(), currOriginalQueryType);
                    return getResults(newNode, indirectionLevel + 1);
                }
            }
        }
        return cache.getCachedResults(node);
    }

    // check cache and additionals for an IP for the name server
    private static InetAddress findNameServerAddress(String nsName) {
        // check the cache (the additionals are already added to the cache)
        DNSNode ipv4Node = new DNSNode(nsName, RecordType.A);
        List<ResourceRecord> matches = new ArrayList<ResourceRecord>(cache.getCachedResults(ipv4Node));
        if (!matches.isEmpty()) {
            return matches.get(0).getInetResult();
        } else {
            return null;
        }
    }

    private static void processResponse(DNSResponse response, DNSNode node) {
        // check size of answers
        if (!response.answers.isEmpty()) {
            // Then the answers are already in the cache and will be taken care of
        } else {
            // nothing in answers
            // check nameServers
            List<ResourceRecord> nsArrAll = new ArrayList<ResourceRecord>(response.nameServers);

            // for each one in nsArr, check cache (since additional is already in cache)
            // find the first nameServer that has an IP
            boolean foundNSAddress = false;

            // just NS
            List<ResourceRecord> nsArr = new ArrayList<ResourceRecord>();
            for (ResourceRecord rec : nsArrAll) {
                if (rec.getType() == RecordType.NS) {
                    nsArr.add(rec);
                }
            }

            if (nsArr.isEmpty()) {
                // done
                return;
            }
            for (ResourceRecord rec : nsArr) {
                String nsName = rec.getTextResult();
                InetAddress inetAddress = findNameServerAddress(nsName);
                if (inetAddress == null) {
                    continue;
                } else {
                    foundNSAddress = true;
                    // found
                    retrieveResultsFromServer(node, inetAddress);
                    break;
                }
            }
            if (!foundNSAddress) {
                // Search rootServer for first NSAddress
                ResourceRecord firstNSRec = nsArr.get(0);
                String nsName = firstNSRec.getTextResult();
                DNSNode nsResolveNode = new DNSNode(nsName, RecordType.A);
                // Query root DNS server for the NS
                retrieveResultsFromServer(nsResolveNode, rootServer);
                // See if result exists in the cache
                InetAddress inetAddress = findNameServerAddress(nsName);
                if (inetAddress == null) {
                    return;
                } else {
                    foundNSAddress = true;
                    // found
                    retrieveResultsFromServer(node, inetAddress);
                    return;
                }
            }
        }
    }

    /**
     * Retrieves DNS results from a specified DNS server. Queries are sent in
     * iterative mode, and the query is repeated with a new server if the provided
     * one is non-authoritative. Results are stored in the cache.
     *
     * @param node   Host name and record type to be used for the query.
     * @param server Address of the server to be used for the query.
     */
    private static void retrieveResultsFromServer(DNSNode node, InetAddress server) {
        DNSQuery query;
        if (querySuccess) {
            query = new DNSQuery(node, random.nextInt(65536));
        } else {
            query = new DNSQuery(node, previousQueryID);
        }
        DNSResponse response;
        try {
            if (verboseTracing) {
                query.print(server);
            }
            query.sendPacket(socket, server);
            response = DNSResponse.receiveDNS(socket);

            querySuccess = true;
            previousQueryID = query.queryID;
            response.addToCache(cache);
            if (response.dnsHeader.RCODE == 3 || response.dnsHeader.RCODE == 5) {
                // do not print, and do not process
                return;
            }

            if (verboseTracing) {
                response.print();
            }
            processResponse(response, node);

        } catch (SocketTimeoutException e) {
            if (querySuccess) {
                previousQueryID = query.queryID;
                querySuccess = false;
                retrieveResultsFromServer(node, server);
            } else {
                querySuccess = true;
            }
        } catch (Exception e) {

        }
        previousQueryID = query.queryID;
    }

    private static void verbosePrintResourceRecord(ResourceRecord record, int rtype) {
        if (verboseTracing)
            System.out.format("       %-30s %-10d %-4s %s\n", record.getHostName(), record.getTTL(),
                    record.getType() == RecordType.OTHER ? rtype : record.getType(), record.getTextResult());
    }

    /**
     * Prints the result of a DNS query.
     *
     * @param node    Host name and record type used for the query.
     * @param results Set of results to be printed for the node.
     */
    private static void printResults(DNSNode node, Set<ResourceRecord> results) {
        if (results.isEmpty())
            System.out.printf("%-30s %-5s %-8d %s\n", node.getHostName(), node.getType(), -1, "0.0.0.0");
        for (ResourceRecord record : results) {
            System.out.printf("%-30s %-5s %-8d %s\n", node.getHostName(), node.getType(), record.getTTL(),
                    record.getTextResult());
        }
    }
}
