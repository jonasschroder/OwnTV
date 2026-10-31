import java.net.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
public class RecognitionProbe {
  static class Subject {
    final Object owner; final Method method;
    Subject(String classes, String stdlib) throws Exception {
      var loader = new URLClassLoader(new URL[]{Path.of(classes).toUri().toURL(),Path.of(stdlib).toUri().toURL()}, null);
      var type = loader.loadClass("tv.own.owntv.features.live.SwedishChannels");
      owner=type.getField("INSTANCE").get(null); method=type.getMethod("recognize",String.class);
    }
    String recognize(String name) throws Exception { var r=method.invoke(owner,name); return String.valueOf(r); }
    double measure(List<String> names) throws Exception {
      long start=System.nanoTime(); long count=0;
      for(var name:names) { var r=method.invoke(owner,name); if(r!=null)count++; }
      if(count!=23)throw new AssertionError("Unexpected recognized count "+count);
      return (System.nanoTime()-start)/1_000_000d;
    }
  }
  static String result(List<Double> values){Collections.sort(values); return "{\"medianMs\":"+values.get(values.size()/2)+",\"p95Ms\":"+values.get((int)Math.ceil(values.size()*.95)-1)+",\"n\":"+values.size()+"}";}
  public static void main(String[] args) throws Exception {
    var old=new Subject(args[0],args[2]); var candidate=new Subject(args[1],args[2]);
    var names=new ArrayList<String>(List.of("SVT1 HD","SVT2 HD","TV3 HD","TV4 HD","Kanal 5 HD","TV6 HD","Sjuan HD","TV8 HD","Kanal 9 HD","TV10 HD","TV4 Hockey HD"));
    for(int i=1;i<=12;i++)names.add("TV4 Sport Live "+i+" HD");
    for(int i=23;i<12000;i++)names.add("Synthetic other channel "+i);
    var edges=List.of("SE | SVT 1 FHD","SVT1 Värmland HD","SVT1 Värmland Highlights","NO | TV4 HD","TV4 Play Hockey","SE: TV Hockey Live 10 1080p","SVT1 +1 HD SE");
    int equal=0;for(var name:names) {if(!old.recognize(name).equals(candidate.recognize(name)))throw new AssertionError(name);equal++;}
    for(var name:edges){if(!old.recognize(name).equals(candidate.recognize(name)))throw new AssertionError(name);equal++;}
    for(int i=0;i<5;i++){old.measure(names);candidate.measure(names);}
    var a=new ArrayList<Double>();var b=new ArrayList<Double>();
    for(int i=0;i<20;i++){if(i%2==0){a.add(old.measure(names));b.add(candidate.measure(names));}else{b.add(candidate.measure(names));a.add(old.measure(names));}}
    System.out.println("{\"environment\":\"host JVM; recognition only, no UI or hardware latency claim\",\"equivalentInputs\":"+equal+",\"baseline\":"+result(a)+",\"candidate\":"+result(b)+"}");
  }
}
