package org.bloomreach.inspections.core.reports

import org.bloomreach.inspections.core.engine.InspectionIssue
import org.bloomreach.inspections.core.engine.InspectionResults
import org.bloomreach.inspections.core.engine.Severity
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Generates HTML format inspection reports with embedded CSS styling.
 */
class HtmlReportGenerator : ReportGenerator {

    override fun generate(results: InspectionResults, outputPath: Path) {
        val html = buildReport(results)
        Files.createDirectories(outputPath.parent)
        Files.writeString(outputPath, html)
    }

    private fun buildReport(results: InspectionResults): String {
        val sb = StringBuilder()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        sb.append("<!DOCTYPE html>\n")
        sb.append("<html lang=\"en\">\n")
        sb.append("<head>\n")
        sb.append("    <meta charset=\"UTF-8\">\n")
        sb.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
        sb.append("    <title>Bloomreach CMS Inspection Report</title>\n")
        sb.append("    <style>\n")
        sb.append(getStyles())
        sb.append("    </style>\n")
        sb.append("</head>\n")
        sb.append("<body>\n")
        sb.append("    <div class=\"container\">\n")

        // Header
        sb.append("        <header>\n")
        sb.append("            <h1>Bloomreach CMS Inspection Report</h1>\n")
        sb.append("            <p class=\"timestamp\">Generated: $timestamp</p>\n")
        sb.append("        </header>\n")

        // Summary Cards
        sb.append("        <section class=\"summary-cards\">\n")
        sb.append("            <div class=\"card total\">\n")
        sb.append("                <div class=\"card-value\">${results.totalIssues}</div>\n")
        sb.append("                <div class=\"card-label\">Total Issues</div>\n")
        sb.append("            </div>\n")
        sb.append("            <div class=\"card error\">\n")
        sb.append("                <div class=\"card-value\">${results.errorCount}</div>\n")
        sb.append("                <div class=\"card-label\">Errors</div>\n")
        sb.append("            </div>\n")
        sb.append("            <div class=\"card warning\">\n")
        sb.append("                <div class=\"card-value\">${results.warningCount}</div>\n")
        sb.append("                <div class=\"card-label\">Warnings</div>\n")
        sb.append("            </div>\n")
        sb.append("            <div class=\"card info\">\n")
        sb.append("                <div class=\"card-value\">${results.infoCount}</div>\n")
        sb.append("                <div class=\"card-label\">Info</div>\n")
        sb.append("            </div>\n")
        sb.append("            <div class=\"card hint\">\n")
        sb.append("                <div class=\"card-value\">${results.hintCount}</div>\n")
        sb.append("                <div class=\"card-label\">Hints</div>\n")
        sb.append("            </div>\n")
        sb.append("        </section>\n")

        // Filter Controls
        sb.append("        <section class=\"filter-section\">\n")
        sb.append("            <h3 class=\"filter-title\">Filter Issues by Severity</h3>\n")
        sb.append("            <div class=\"filter-controls\">\n")
        sb.append("                <button class=\"filter-btn active\" data-filter=\"all\" onclick=\"filterByAllSeverities()\">All</button>\n")
        sb.append("                <button class=\"filter-btn\" data-filter=\"error\" onclick=\"filterBySeverity('error')\">Errors Only</button>\n")
        sb.append("                <button class=\"filter-btn\" data-filter=\"warning\" onclick=\"filterBySeverity('warning')\">Warnings Only</button>\n")
        sb.append("                <button class=\"filter-btn\" data-filter=\"info\" onclick=\"filterBySeverity('info')\">Info Only</button>\n")
        sb.append("                <button class=\"filter-btn\" data-filter=\"hint\" onclick=\"filterBySeverity('hint')\">Hints Only</button>\n")
        sb.append("            </div>\n")
        sb.append("        </section>\n")

        // Issues by Category
        if (results.issuesByCategory.isNotEmpty()) {
            sb.append("        <section class=\"section\">\n")
            sb.append("            <h2>Issues by Category</h2>\n")
            sb.append("            <table class=\"data-table\">\n")
            sb.append("                <thead>\n")
            sb.append("                    <tr>\n")
            sb.append("                        <th>Category</th>\n")
            sb.append("                        <th>Count</th>\n")
            sb.append("                        <th>Percentage</th>\n")
            sb.append("                    </tr>\n")
            sb.append("                </thead>\n")
            sb.append("                <tbody>\n")
            results.issuesByCategory.forEach { (category, issues) ->
                val percentage = if (results.totalIssues > 0) {
                    (issues.size.toDouble() / results.totalIssues * 100).toInt()
                } else {
                    0
                }
                sb.append("                    <tr>\n")
                sb.append("                        <td><strong>${escapeHtml(category.displayName)}</strong></td>\n")
                sb.append("                        <td>${issues.size}</td>\n")
                sb.append("                        <td>\n")
                sb.append("                            <div class=\"progress-bar\">\n")
                sb.append("                                <div class=\"progress-fill\" style=\"width: ${percentage}%\"></div>\n")
                sb.append("                            </div>\n")
                sb.append("                            <span class=\"percentage\">${percentage}%</span>\n")
                sb.append("                        </td>\n")
                sb.append("                    </tr>\n")
            }
            sb.append("                </tbody>\n")
            sb.append("            </table>\n")
            sb.append("        </section>\n")
        }

        // Issues by Severity
        if (results.issuesBySeverity.isNotEmpty()) {
            sb.append("        <section class=\"section\">\n")
            sb.append("            <h2>Issues by Severity</h2>\n")
            sb.append("            <table class=\"data-table\">\n")
            sb.append("                <thead>\n")
            sb.append("                    <tr>\n")
            sb.append("                        <th>Severity</th>\n")
            sb.append("                        <th>Count</th>\n")
            sb.append("                        <th>Distribution</th>\n")
            sb.append("                    </tr>\n")
            sb.append("                </thead>\n")
            sb.append("                <tbody>\n")
            results.issuesBySeverity.forEach { (severity, issues) ->
                val percentage = if (results.totalIssues > 0) {
                    (issues.size.toDouble() / results.totalIssues * 100).toInt()
                } else {
                    0
                }
                val severityClass = severity.name.lowercase()
                sb.append("                    <tr>\n")
                sb.append("                        <td><span class=\"severity-badge $severityClass\">${severity.name}</span></td>\n")
                sb.append("                        <td>${issues.size}</td>\n")
                sb.append("                        <td>\n")
                sb.append("                            <div class=\"progress-bar\">\n")
                sb.append("                                <div class=\"progress-fill $severityClass\" style=\"width: ${percentage}%\"></div>\n")
                sb.append("                            </div>\n")
                sb.append("                            <span class=\"percentage\">${percentage}%</span>\n")
                sb.append("                        </td>\n")
                sb.append("                    </tr>\n")
            }
            sb.append("                </tbody>\n")
            sb.append("            </table>\n")
            sb.append("        </section>\n")
        }

        // Detailed Issues
        if (results.issues.isNotEmpty()) {
            sb.append("        <section class=\"section\">\n")
            sb.append("            <h2>Detailed Issues</h2>\n")

            // Group by file
            val issuesByFile = results.issues.groupBy { it.file.path.toString() }

            issuesByFile.forEach { (filePath, fileIssues) ->
                sb.append("            <div class=\"file-section\">\n")
                sb.append("                <h3 class=\"file-path\">${escapeHtml(filePath)}</h3>\n")
                sb.append("                <div class=\"file-stats\">${fileIssues.size} issue(s)</div>\n")

                fileIssues.sortedBy { it.range.startLine }.forEach { issue ->
                    val severityClass = issue.severity.name.lowercase()

                    sb.append("                <div class=\"issue $severityClass\" data-severity=\"$severityClass\">\n")
                    sb.append("                    <div class=\"issue-header\">\n")
                    sb.append("                        <span class=\"severity-badge $severityClass\">${issue.severity.name}</span>\n")
                    sb.append("                        <span class=\"issue-location\">Line ${issue.range.startLine}:${issue.range.startColumn}</span>\n")
                    sb.append("                    </div>\n")
                    sb.append("                    <div class=\"issue-title\">${escapeHtml(issue.message)}</div>\n")
                    sb.append("                    <div class=\"issue-meta\">\n")
                    sb.append("                        <span class=\"inspection-id\">${escapeHtml(issue.inspection.id)}</span>\n")
                    sb.append("                        <span class=\"category\">${escapeHtml(issue.inspection.category.displayName)}</span>\n")
                    sb.append("                    </div>\n")

                    // Code snippet with context
                    val codeSnippet = extractCodeSnippet(issue, 3)
                    if (codeSnippet.isNotBlank()) {
                        sb.append("                    <div class=\"code-snippet-container\">\n")
                        sb.append(codeSnippet)
                        sb.append("                    </div>\n")
                    }

                    // Description
                    if (issue.description.isNotBlank()) {
                        sb.append("                    <div class=\"issue-description\">\n")
                        sb.append("                        <pre>${escapeHtml(issue.description)}</pre>\n")
                        sb.append("                    </div>\n")
                    }

                    sb.append("                </div>\n")
                }

                sb.append("            </div>\n")
            }

            sb.append("        </section>\n")
        } else {
            sb.append("        <section class=\"section\">\n")
            sb.append("            <div class=\"no-issues\">\n")
            sb.append("                <h2>No Issues Found</h2>\n")
            sb.append("                <p>Your project looks great! All inspections passed.</p>\n")
            sb.append("            </div>\n")
            sb.append("        </section>\n")
        }

        sb.append("        <footer>\n")
        sb.append("            <p>Generated by <strong>Bloomreach CMS Mega-Inspections Analysis Tool</strong></p>\n")
        sb.append("            <p class=\"footer-note\">For more information, visit <a href=\"https://github.com/bloomreach\">Bloomreach on GitHub</a></p>\n")
        sb.append("        </footer>\n")
        sb.append("    </div>\n")
        sb.append("    <script>\n")
        sb.append("        function filterBySeverity(severity) {\n")
        sb.append("            const issues = document.querySelectorAll('.issue');\n")
        sb.append("            const fileSections = document.querySelectorAll('.file-section');\n")
        sb.append("            const buttons = document.querySelectorAll('.filter-btn');\n")
        sb.append("            \n")
        sb.append("            // Update button states\n")
        sb.append("            buttons.forEach(btn => btn.classList.remove('active'));\n")
        sb.append("            document.querySelector(`[data-filter=\"\${severity}\"]`).classList.add('active');\n")
        sb.append("            \n")
        sb.append("            // Filter issues\n")
        sb.append("            issues.forEach(issue => {\n")
        sb.append("                if (issue.dataset.severity === severity) {\n")
        sb.append("                    issue.classList.remove('hidden-issue');\n")
        sb.append("                } else {\n")
        sb.append("                    issue.classList.add('hidden-issue');\n")
        sb.append("                }\n")
        sb.append("            });\n")
        sb.append("            \n")
        sb.append("            // Hide file sections with no visible issues\n")
        sb.append("            fileSections.forEach(section => {\n")
        sb.append("                const visibleIssues = section.querySelectorAll('.issue:not(.hidden-issue)');\n")
        sb.append("                if (visibleIssues.length === 0) {\n")
        sb.append("                    section.classList.add('hidden-issue');\n")
        sb.append("                } else {\n")
        sb.append("                    section.classList.remove('hidden-issue');\n")
        sb.append("                }\n")
        sb.append("            });\n")
        sb.append("        }\n")
        sb.append("        \n")
        sb.append("        function filterByAllSeverities() {\n")
        sb.append("            const issues = document.querySelectorAll('.issue');\n")
        sb.append("            const fileSections = document.querySelectorAll('.file-section');\n")
        sb.append("            const buttons = document.querySelectorAll('.filter-btn');\n")
        sb.append("            \n")
        sb.append("            // Update button states\n")
        sb.append("            buttons.forEach(btn => btn.classList.remove('active'));\n")
        sb.append("            document.querySelector('[data-filter=\"all\"]').classList.add('active');\n")
        sb.append("            \n")
        sb.append("            // Show all issues\n")
        sb.append("            issues.forEach(issue => {\n")
        sb.append("                issue.classList.remove('hidden-issue');\n")
        sb.append("            });\n")
        sb.append("            \n")
        sb.append("            // Show all file sections\n")
        sb.append("            fileSections.forEach(section => {\n")
        sb.append("                section.classList.remove('hidden-issue');\n")
        sb.append("            });\n")
        sb.append("        }\n")
        sb.append("    </script>\n")
        sb.append("</body>\n")
        sb.append("</html>\n")

        return sb.toString()
    }

    private fun extractCodeSnippet(issue: InspectionIssue, contextLines: Int): String {
        return try {
            val fileContent = issue.file.readText()
            val lines = fileContent.split("\n")

            if (lines.isEmpty()) return ""

            val issueLineIndex = issue.range.startLine - 1
            val startLine = maxOf(0, issueLineIndex - contextLines)
            val endLine = minOf(lines.size - 1, issueLineIndex + contextLines)

            val sb = StringBuilder()
            sb.append("                        <pre class=\"code-snippet\">")

            for (i in startLine..endLine) {
                val lineNumber = i + 1
                val lineContent = escapeHtml(lines[i])
                val isProblematicLine = lineNumber == issue.range.startLine
                val lineClass = if (isProblematicLine) " class=\"problematic-line\"" else ""

                sb.append("<span$lineClass>")
                sb.append("<span class=\"line-number\">$lineNumber</span> ")
                sb.append(lineContent)
                sb.append("</span>\n")
            }

            sb.append("</pre>")
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun getStyles(): String {
        return """
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
    line-height: 1.6;
    color: #333;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    padding: 20px;
}

.container {
    max-width: 1200px;
    margin: 0 auto;
    background: white;
    border-radius: 12px;
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
    overflow: hidden;
}

header {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    padding: 40px;
    text-align: center;
}

header h1 {
    font-size: 2.5rem;
    margin-bottom: 10px;
    font-weight: 700;
}

.timestamp {
    font-size: 1rem;
    opacity: 0.9;
}

.summary-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 20px;
    padding: 40px;
    background: #f8f9fa;
}

.card {
    background: white;
    border-radius: 8px;
    padding: 24px;
    text-align: center;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    transition: transform 0.2s, box-shadow 0.2s;
}

.card:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 16px rgba(0, 0, 0, 0.15);
}

.card-value {
    font-size: 2.5rem;
    font-weight: 700;
    margin-bottom: 8px;
}

.card-label {
    font-size: 0.9rem;
    color: #666;
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.card.total .card-value { color: #667eea; }
.card.error .card-value { color: #dc3545; }
.card.warning .card-value { color: #ffc107; }
.card.info .card-value { color: #17a2b8; }
.card.hint .card-value { color: #6c757d; }

.section {
    padding: 40px;
    border-bottom: 1px solid #e9ecef;
}

.section:last-child {
    border-bottom: none;
}

h2 {
    font-size: 1.8rem;
    margin-bottom: 24px;
    color: #2c3e50;
    border-bottom: 3px solid #667eea;
    padding-bottom: 8px;
}

h3 {
    font-size: 1.3rem;
    margin-bottom: 12px;
    color: #34495e;
}

.filter-section {
    padding: 30px 40px;
    background: #f8f9fa;
    border-bottom: 2px solid #e9ecef;
}

.filter-title {
    font-size: 1.1rem;
    color: #34495e;
    margin-bottom: 16px;
    font-weight: 600;
}

.filter-controls {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
}

.filter-btn {
    padding: 10px 20px;
    border: 2px solid #d0d0d0;
    background: white;
    color: #333;
    border-radius: 6px;
    cursor: pointer;
    font-size: 0.95rem;
    font-weight: 600;
    transition: all 0.2s ease;
}

.filter-btn:hover {
    border-color: #667eea;
    color: #667eea;
    transform: translateY(-2px);
}

.filter-btn.active {
    background: #667eea;
    color: white;
    border-color: #667eea;
    box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.issue.hidden-issue {
    display: none;
}

.file-section.hidden-issue {
    display: none;
}

.data-table {
    width: 100%;
    border-collapse: collapse;
    margin-top: 16px;
}

.data-table thead {
    background: #667eea;
    color: white;
}

.data-table th {
    padding: 12px;
    text-align: left;
    font-weight: 600;
    text-transform: uppercase;
    font-size: 0.85rem;
    letter-spacing: 0.5px;
}

.data-table td {
    padding: 12px;
    border-bottom: 1px solid #e9ecef;
}

.data-table tbody tr:hover {
    background: #f8f9fa;
}

.progress-bar {
    display: inline-block;
    width: 200px;
    height: 20px;
    background: #e9ecef;
    border-radius: 10px;
    overflow: hidden;
    vertical-align: middle;
    margin-right: 8px;
}

.progress-fill {
    height: 100%;
    background: #667eea;
    transition: width 0.3s ease;
}

.progress-fill.error { background: #dc3545; }
.progress-fill.warning { background: #ffc107; }
.progress-fill.info { background: #17a2b8; }
.progress-fill.hint { background: #6c757d; }

.percentage {
    font-weight: 600;
    color: #666;
}

.file-section {
    margin-bottom: 32px;
    border: 1px solid #e9ecef;
    border-radius: 8px;
    overflow: hidden;
}

.file-path {
    background: #f8f9fa;
    padding: 16px 20px;
    margin: 0;
    font-family: 'Courier New', monospace;
    font-size: 1rem;
    border-bottom: 1px solid #e9ecef;
}

.file-stats {
    background: #e9ecef;
    padding: 8px 20px;
    font-size: 0.9rem;
    color: #666;
}

.issue {
    padding: 20px;
    border-left: 4px solid #667eea;
    margin: 16px 20px;
    background: #f8f9fa;
    border-radius: 4px;
}

.issue.error { border-left-color: #dc3545; background: #fff5f5; }
.issue.warning { border-left-color: #ffc107; background: #fffdf5; }
.issue.info { border-left-color: #17a2b8; background: #f5fcff; }
.issue.hint { border-left-color: #6c757d; background: #f8f9fa; }

.issue-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
}

.severity-badge {
    display: inline-block;
    padding: 4px 12px;
    border-radius: 16px;
    font-size: 0.85rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.severity-badge.error { background: #dc3545; color: white; }
.severity-badge.warning { background: #ffc107; color: #333; }
.severity-badge.info { background: #17a2b8; color: white; }
.severity-badge.hint { background: #6c757d; color: white; }

.issue-location {
    font-family: 'Courier New', monospace;
    font-size: 0.9rem;
    color: #666;
    background: white;
    padding: 4px 8px;
    border-radius: 4px;
}

.issue-title {
    font-size: 1.1rem;
    font-weight: 600;
    color: #2c3e50;
    margin-bottom: 12px;
}

.issue-meta {
    display: flex;
    gap: 12px;
    margin-bottom: 12px;
}

.inspection-id, .category {
    font-size: 0.85rem;
    padding: 4px 8px;
    border-radius: 4px;
    background: white;
    color: #666;
}

.code-snippet-container {
    margin-top: 12px;
    margin-bottom: 12px;
}

.code-snippet {
    display: block;
    background: #f5f5f5;
    border: 1px solid #d0d0d0;
    border-radius: 4px;
    padding: 12px;
    font-family: 'Courier New', 'Monaco', monospace;
    font-size: 0.85rem;
    line-height: 1.5;
    color: #333;
    overflow-x: auto;
    white-space: pre;
    margin: 0;
}

.code-snippet > span {
    display: block;
}

.code-snippet .line-number {
    display: inline-block;
    width: 3em;
    text-align: right;
    color: #999;
    user-select: none;
    margin-right: 0.5em;
    border-right: 1px solid #d0d0d0;
    padding-right: 0.5em;
}

.code-snippet .problematic-line {
    background: #fff3cd;
    border-left: 3px solid #ffc107;
    padding-left: 9px;
}

.code-snippet .problematic-line .line-number {
    color: #ffc107;
    font-weight: bold;
}

.issue-description {
    margin-top: 12px;
    padding: 16px;
    background: white;
    border-radius: 4px;
    border: 1px solid #e9ecef;
}

.issue-description pre {
    white-space: pre-wrap;
    word-wrap: break-word;
    font-family: 'Courier New', monospace;
    font-size: 0.9rem;
    line-height: 1.5;
    color: #333;
}

.no-issues {
    text-align: center;
    padding: 60px 40px;
}

.no-issues h2 {
    font-size: 2.5rem;
    color: #28a745;
    border: none;
    margin-bottom: 16px;
}

.no-issues p {
    font-size: 1.2rem;
    color: #666;
}

footer {
    background: #2c3e50;
    color: white;
    padding: 30px 40px;
    text-align: center;
}

footer p {
    margin: 8px 0;
}

footer a {
    color: #667eea;
    text-decoration: none;
}

footer a:hover {
    text-decoration: underline;
}

.footer-note {
    font-size: 0.9rem;
    opacity: 0.8;
}

@media print {
    body {
        background: white;
        padding: 0;
    }

    .container {
        box-shadow: none;
    }

    .card:hover {
        transform: none;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    }

    .issue-details summary {
        display: none;
    }

    .issue-details[open] .issue-description {
        display: block !important;
    }
}

@media (max-width: 768px) {
    .summary-cards {
        grid-template-columns: 1fr;
    }

    header h1 {
        font-size: 1.8rem;
    }

    .section {
        padding: 20px;
    }

    .progress-bar {
        width: 100px;
    }
}
"""
    }
}
