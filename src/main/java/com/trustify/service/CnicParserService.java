package com.trustify.service;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CnicParserService {

    public String extractedCnicNumber(String text){
        Pattern pattern = Pattern.compile("\\d{5}[- ]?\\d{7}[- ]?\\d");

        Matcher matcher = pattern.matcher(text);
        if (matcher.find()){
            return matcher.group();
        }else {
            return null;
        }
    }

    public String extractedName(String text){
        String[] lines = text.split("\\r?\\n");
        for(String line: lines){
            line = line.trim();
            if (line.matches("[A-Z ]{5,}")){
                if (!line.contains("ISLAMIC")
                && !line.contains("REPUBLIC")
                && !line.contains("PAKISTAN")){
                    return line;
                }
            }
        }
        return null;
    }
}
