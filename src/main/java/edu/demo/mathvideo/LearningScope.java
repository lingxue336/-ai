package edu.demo.mathvideo;

import java.util.*;
import java.util.regex.*;

/** Deterministic guard for a frequent false semantic match: different discriminant cases. */
final class LearningScope {
    private static final Pattern DELTA=Pattern.compile("(?:Δ|δ|delta|判别式(?:的值)?|b[²2]\\s*[-−]\\s*4ac)\\s*([<>＝=＜＞≥≤])\\s*([-−]?\\d+(?:\\.\\d+)?)",Pattern.CASE_INSENSITIVE);
    static Set<String> numericCases(String text) {
        Set<String> cases=new LinkedHashSet<>();Matcher m=DELTA.matcher(text==null?"":text);
        while(m.find()) {
            double value=Double.parseDouble(m.group(2).replace('−','-'));String relation=m.group(1);
            if(relation.equals("=")||relation.equals("＝"))cases.add(value<0?"negative":value>0?"positive":"zero");
            else if(value==0) switch(relation){
                case "<","＜" -> cases.add("negative");case ">","＞" -> cases.add("positive");
                case "≥" -> {cases.add("positive");cases.add("zero");}case "≤" -> {cases.add("negative");cases.add("zero");}
            }
        }
        return cases;
    }
    static boolean compatible(String evidence,String studentWork,String clipDescription) {
        Set<String> need=new LinkedHashSet<>();
        Matcher computed=Pattern.compile("(?im)(?:Δ|δ|delta)\\s*=[^\\n]*=\\s*([-−]?\\d+(?:\\.\\d+)?)\\s*(?:[。；;]|$)").matcher(studentWork==null?"":studentWork);
        if(computed.find()){double value=Double.parseDouble(computed.group(1).replace('−','-'));need.add(value<0?"negative":value>0?"positive":"zero");}
        if(need.isEmpty())need=numericCases(evidence);
        if(need.isEmpty())need=numericCases(studentWork);
        // Evidence may append a general table of all three cases after discussing the actual error.
        if(need.size()>1)need=Set.of(need.iterator().next());
        Set<String> covers=numericCases(clipDescription);
        if(clipDescription.contains("无实数根")||clipDescription.contains("没有实数根")||clipDescription.contains("小于零")||clipDescription.contains("小于0"))covers.add("negative");
        if(clipDescription.contains("两个相等")||clipDescription.contains("等于零")||clipDescription.contains("等于0"))covers.add("zero");
        if(clipDescription.contains("两个不相等")||clipDescription.contains("两个不等")||clipDescription.contains("大于零")||clipDescription.contains("大于0"))covers.add("positive");
        // Unknown scopes still require model verification; known disjoint conditions can never match.
        return need.isEmpty()||covers.isEmpty()||!Collections.disjoint(need,covers);
    }
}
