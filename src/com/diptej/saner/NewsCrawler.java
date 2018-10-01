package com.diptej.saner;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import edu.uci.ics.crawler4j.crawler.Page;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import edu.uci.ics.crawler4j.url.WebURL;

public class NewsCrawler extends WebCrawler {

	private final static Pattern FILTERS = Pattern.compile(".*(\\.(css|js|gif|jpg" + "|png|mp3|mp4|zip|gz))$");

	int uniqueURLsInside;
	int uniqueURLsOutside;

	int fetches;
	int succeededFetches;
	int failedFetches;

	int downloadSizeInBytes;
	int visitCount;

	Map<Integer, Integer> statusCodeCounts;
	Map<String, Integer> urlStatusCodeMap;
	Map<String, Integer> contentTypeCounts;
	Map<String, String[]> downloadSizeStats;

	Set<String> okUrlSet;
	Set<String> nokUrlSet;

	Set<String> insideUrls;
	Set<String> outsideUrls;

	List<String[]> okUrls;

	int[] fileSizeRangeCounts;
	int[] validUrlCounts;

	@Override
	public void onStart() {
		urlStatusCodeMap = new HashMap<>();
		statusCodeCounts = new HashMap<>();
		downloadSizeStats = new HashMap<>();
		contentTypeCounts = new HashMap<>();

		okUrlSet = new HashSet<>();
		nokUrlSet = new HashSet<>();
		insideUrls = new HashSet<>();
		outsideUrls = new HashSet<>();

		okUrls = new ArrayList<>();

		validUrlCounts = new int[] { 0, 0 };
		fileSizeRangeCounts = new int[] { 0, 0, 0, 0, 0 };
	}

	@Override
	public boolean shouldVisit(Page referringPage, WebURL url) {
		String href = url.getURL().toLowerCase();

		if (href.startsWith("http://www.latimes.com/") || href.startsWith("https://www.latimes.com/")) {
			validUrlCounts[0]++;
			okUrls.add(new String[] { url.getURL(), "OK" });

			if (!okUrlSet.contains(url.getURL())) {
				okUrlSet.add(url.getURL());
			}
		} else {
			validUrlCounts[1]++;
			okUrls.add(new String[] { url.getURL(), "N_OK" });

			if (!nokUrlSet.contains(url.getURL())) {
				nokUrlSet.add(url.getURL());
			}
		}

		return !FILTERS.matcher(href).matches()
				&& (href.startsWith("http://www.latimes.com/") || href.startsWith("https://www.latimes.com/"));
	}

	@Override
	public void visit(Page page) {
		String url = page.getWebURL().getURL().replaceAll(",", "-");

		for (WebURL outgoingUrl : page.getParseData().getOutgoingUrls()) {
			String outUrl = outgoingUrl.getURL();
			if (outUrl.startsWith("http://www.latimes.com/") || outUrl.startsWith("https://www.latimes.com/")) {
				insideUrls.add(outUrl);
			} else {
				outsideUrls.add(outUrl);
			}
		}

		String contentType = page.getContentType();
		if (contentType.startsWith("text/html")) {
			contentType = "text/html";
		}
		Integer count = contentTypeCounts.putIfAbsent(contentType, 1);
		if (count != null) {
			contentTypeCounts.put(contentType, ++count);
		}

		downloadSizeInBytes = page.getContentData().length;

		if (downloadSizeInBytes < 1024) {
			fileSizeRangeCounts[0] += 1;
		} else if (downloadSizeInBytes < 1024 * 10) {
			fileSizeRangeCounts[1] += 1;
		} else if (downloadSizeInBytes < 1024 * 100) {
			fileSizeRangeCounts[2] += 1;
		} else if (downloadSizeInBytes < 1024 * 1000) {
			fileSizeRangeCounts[3] += 1;
		} else {
			fileSizeRangeCounts[4] += 1;
		}

		int numOutlinks = page.getParseData().getOutgoingUrls().size();
		String[] dArr = new String[] { String.valueOf(downloadSizeInBytes), String.valueOf(numOutlinks), contentType };
		downloadSizeStats.put(url, dArr);

		System.out.println("Visit count : " + ++visitCount);
	}

	@Override
	protected void handlePageStatusCode(WebURL webUrl, int statusCode, String statusDescription) {
		String url = String.valueOf(webUrl).replaceAll(",", "-");
		urlStatusCodeMap.put(url, statusCode);

		Integer count = statusCodeCounts.putIfAbsent(statusCode, 1);
		if (count != null) {
			statusCodeCounts.put(statusCode, ++count);
		}
	}

	@Override
	public void onBeforeExit() {
		PrintWriter pwfetch = null;
		try {
			pwfetch = new PrintWriter(new File("fetch_latimes.csv"));
		} catch (Exception e) {

			e.printStackTrace();
		}
		for (String url : urlStatusCodeMap.keySet()) {
			StringBuilder sb = new StringBuilder();

			sb.append(url);
			sb.append(',');
			sb.append(urlStatusCodeMap.get(url));
			sb.append('\n');

			pwfetch.write(sb.toString());
		}

		pwfetch.close();

		pwfetch = null;
		try {
			pwfetch = new PrintWriter(new File("visit_latimes.csv"));
		} catch (Exception e) {

			e.printStackTrace();
		}
		System.out.println(downloadSizeStats);
		for (String url : downloadSizeStats.keySet()) {
			String[] dStats = downloadSizeStats.get(url);

			StringBuilder sb = new StringBuilder();

			sb.append(url);
			sb.append(',');
			sb.append(dStats[0]);
			sb.append(',');
			sb.append(dStats[1]);
			sb.append(',');
			sb.append(dStats[2]);
			sb.append('\n');

			pwfetch.write(sb.toString());
		}

		pwfetch.close();

		//		pwfetch = null;
		//		try {
		//			pwfetch = new PrintWriter(new File("urls_latimes.csv"));
		//		} catch (Exception e) {
		//
		//			e.printStackTrace();
		//		}
		//		for (String[] strArr : okUrls) {
		//			StringBuilder sb = new StringBuilder();
		//
		//			sb.append(strArr[0]);
		//			sb.append(',');
		//			sb.append(strArr[1]);
		//			sb.append('\n');
		//
		//			pwfetch.write(sb.toString());
		//		}
		//
		//		pwfetch.close();

		int totalAttempts = 0;
		for (Map.Entry<Integer, Integer> sCode : statusCodeCounts.entrySet()) {
			totalAttempts += sCode.getValue();
		}

		System.out.println("\nFetch stats:");
		System.out.println("Attempted: " + totalAttempts);
		System.out.println("Succeeded: " + statusCodeCounts.get(200));
		System.out.println("Failed: " + (totalAttempts - statusCodeCounts.get(200)));

		System.out.println("\nValid URL counts: " + validUrlCounts[0] + " " + validUrlCounts[1]);

		System.out.println("\nOutgoing URLs:");
		System.out.println("unique URLs extracted: " + (insideUrls.size() + outsideUrls.size()));
		System.out.println("unique URLs within News Site: " + insideUrls.size());
		System.out.println("outside URLs within News Site: " + outsideUrls.size());

		System.out.println("\nStatus Codes: ");
		System.out.println(statusCodeCounts);

		System.out.println("\nContent types: ");
		//		System.out.println(contentTypeCounts);
		for (Map.Entry<String, Integer> ct : contentTypeCounts.entrySet()) {
			System.out.println(ct.getKey() + "     " + ct.getValue());
		}

		System.out.println("\nFile size counts: ");
		System.out.println(fileSizeRangeCounts[0] + " " + fileSizeRangeCounts[1] + " " + fileSizeRangeCounts[2] + " "
				+ fileSizeRangeCounts[3] + " " + fileSizeRangeCounts[4]);
	}
}
