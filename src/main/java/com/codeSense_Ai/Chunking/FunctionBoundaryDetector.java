package com.codeSense_Ai.Chunking;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class FunctionBoundaryDetector {
    private static final Pattern JAVA_METHOD_START = Pattern.compile(
            "^\\s*(public|private|protected|static|final|abstract|synchronized)" +
                    "[\\w\\s<>\\[\\],?]*\\s+\\w+\\s*\\([^)]*\\).*\\{\\s*$"
    );

    // Matches Python function definitions: "def function_name(..."
    // Indentation-aware — tracks indent level to find where the function ends
    private static final Pattern PYTHON_FUNCTION_START = Pattern.compile(
            "^(\\s*)def\\s+\\w+\\s*\\(.*\\).*:\\s*$"
    );


    public boolean isFunctionStart(String line , String language){
        return switch (language.toLowerCase()){
            case "java" -> JAVA_METHOD_START.matcher(line).matches();
            case "python" , "py" -> PYTHON_FUNCTION_START.matcher(line).matches();
            case "javascript",
                 "typescript",
                 "js" , "ts" -> isJsFunctionStart(line);
            default -> false;
        };
    }

    public boolean isJsFunctionStart(String line){
        String trimmed = line.trim();
        // Arrow functions, regular functions, class methods
        return trimmed.matches("^(export\\s+)?(async\\s+)?function\\s+\\w+.*\\{\\s*$")
                || trimmed.matches("^(const|let|var)\\s+\\w+\\s*=\\s*(async\\s*)?\\(.*\\).*=>.*\\{\\s*$")
                || trimmed.matches("^(public|private|protected|static|async)?\\s*\\w+\\s*\\(.*\\).*\\{\\s*$");

    }
    public int FindFunctionEnd(List<String> lines , int startIndex , String language){
        if(language.equals("python") || language.equals("py")){
            return findByPythonEnd(lines , startIndex);
        }

        return findBraceMatchENd(lines , startIndex);


    }

    public int findByPythonEnd(List<String> lines , int startIndex){
        String startLine = lines.get(startIndex);
        int countIndent = countLeadingSpaces(startLine);

        for (int i = startIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue; // blank lines don't count
            int currentIndent = countLeadingSpaces(line);
            if (currentIndent <= currentIndent) {
                return i - 1; // line before indentation dropped = function end
            }
        }
        return lines.size() - 1;

    }
    private int countLeadingSpaces(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ')  count++;
            else if (c == '\t') count += 4;
            else break;
        }
        return count;
    }
    public int findBraceMatchENd(List<String> lines , int startIndex){
        int depth = 0;
        for (int i = startIndex; i < lines.size(); i++) {
            String line = lines.get(i);
            for (char c : line.toCharArray()) {
                if (c == '{') depth++;
                if (c == '}') depth--;
            }
            if (depth == 0 && i > startIndex) {
                return i; // found the closing brace of the function
            }
        }
        return lines.size() - 1;
    }

}