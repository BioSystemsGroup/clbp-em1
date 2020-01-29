/*
 * Copyright 2020 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 */
package clbp.ctrl;

import clbp.view.ObsComps;
import java.io.FileNotFoundException;

public class Batch {
  long seed = -Integer.MAX_VALUE;
  Parameters params = null;
  public sim.engine.SimState state = null;
  public static String expName = "notset";
  private static String out_dir_name = null;
  static java.io.File dir = null;
  clbp.model.Model model = null;
  String paramString;
  
  public Batch(String en, java.io.File pf) throws FileNotFoundException {
    if (en != null && !en.equals("")) expName = en;
    else throw new RuntimeException("Experiment name cannot be null or empty.");
    java.io.FileInputStream pfis = new java.io.FileInputStream(pf);
    paramString = new java.util.Scanner(pfis).useDelimiter("\\A").next();
    state = new sim.engine.SimState(System.currentTimeMillis());
  }
  public final void load() {
    out_dir_name = setupOutput(expName);
    clbp.ctrl.Parameters p = clbp.ctrl.Parameters.readOneOfYou(paramString);
    params = p;
    Number seed_n = params.batch.get("seed");
    if (seed_n != null) {
      seed = seed_n.longValue();
      state.random = new ec.util.MersenneTwisterFast(seed);
    } else {
      seed = state.seed();
      params.batch.put("seed",seed);
    }

    writeParameters(out_dir_name, params);

    model = new clbp.model.Model(params);
    model.init(state, params.model.get("timeLimit").doubleValue(), params.model.get("cyclePerTime").doubleValue());
    model.instantiate();
    // schedule the components
    model.comps.forEach((c) -> { state.schedule.scheduleOnce(c,clbp.model.Model.MODEL_ORDER); });
    // schedule the model
    state.schedule.scheduleOnce(model,clbp.model.Model.MODEL_ORDER);
        
    // schedule an observer
    ObsComps oc = new ObsComps(expName, p);
    oc.init(dir, model);
    state.schedule.scheduleOnce(oc,clbp.view.Obs.VIEW_ORDER);
  }
  
  public void go() {
    while (!model.finished || !model.finished)
      state.schedule.step(state);
    log("Batch.go() - Submodels are finished!");
  }
  
  public void finish() {
    log.close();
  }

  private static java.io.PrintWriter log = null;
  public static void log(String entry) { log.println(entry); log.flush(); }
  
  static String setupOutput(String en) {
    final String DATE_FORMAT = "yyyy-MM-dd-HHmmss";
    final java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(DATE_FORMAT);
    StringBuffer date_s = new StringBuffer("");
    sdf.format(new java.util.Date(System.currentTimeMillis()), date_s,
            new java.text.FieldPosition(0));
    String dirName = date_s.toString();

    // create a directory using the current date and time
    dir = new java.io.File(dirName);
    if (!dir.exists()) dir.mkdir();
    
    // initialize the output for run-time messages
    try {
      log = new java.io.PrintWriter(new java.io.File(dirName
              + java.io.File.separator + en + "-output.txt"));
    } catch (java.io.FileNotFoundException fnfe) {
      throw new RuntimeException("Couldn't open " + dirName
              + java.io.File.separator + en + ".txt", fnfe);
    }
    return dirName;
  }
  
  private static void writeParameters(String dirName, Parameters p) {
    try {
      java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(dirName 
              + java.io.File.separator 
              + "parameters-" + clbp.Main.MAJOR_VERSION + "-" + System.currentTimeMillis()+".json"));
      p.version = clbp.Main.MAJOR_VERSION+" Subversion"+clbp.Main.MINOR_VERSION;
      fw.write(describe(p));
      fw.close();
    } catch (java.io.IOException ioe) {
      System.exit(-1);
    }
  }
  public static void writeToFile(String fileName, String thingToWrite) {
    try {
      java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(out_dir_name 
              + java.io.File.separator 
              + fileName+".json"));
      fw.write(thingToWrite);
      fw.close();
    } catch (java.io.IOException ioe) {
      System.exit(-1);
    }
  }
  public static String describe(Object o) {
    com.owlike.genson.Genson g = new com.owlike.genson.Genson();
    String json = g.serialize(o);
    return json;
  }
  
  public double getMaxCycle() {
    return Math.max(model.timeLimit*model.cyclePerTime, model.timeLimit*model.cyclePerTime);
  }

  /**
   * Utilities
   **/  
  private static java.util.Map<Class,java.util.Collection<java.io.File>> fileMap = new java.util.HashMap<Class,java.util.Collection<java.io.File>>();
  public static Object readNext(Class aClass) {
    com.owlike.genson.Genson g = new com.owlike.genson.Genson();
    java.util.Collection<java.io.File> files = null;
    if (!fileMap.containsKey(aClass)) {
      String in_dir_name = new java.io.File("").getAbsolutePath()+java.io.File.separator+"cfg";
      files = listFiles(in_dir_name, aClass.getSimpleName()+"-*.json");
      fileMap.put(aClass, files);
    } else {
      files = fileMap.get(aClass);
    }
    java.io.File nextFile = files.stream().findFirst().get();
    files.remove(nextFile);
    String json = null;
    try {
      json = new java.util.Scanner(nextFile).useDelimiter("\\A").next();
    } catch(java.io.FileNotFoundException fnfe) { throw new RuntimeException("Cannot open "+nextFile.getPath(),fnfe); }
    System.out.println("Deserializing\n"+json);
    Object o = g.deserialize(json, aClass);
    if (o == null) throw new RuntimeException("Problem loading "+aClass.getSimpleName()+".");
    return o;
  }
  private static java.util.Collection<java.io.File> listFiles(String dir, String pattern) {
    java.io.File directory = new java.io.File(dir);
    return org.apache.commons.io.FileUtils.listFiles(directory, 
            new org.apache.commons.io.filefilter.WildcardFileFilter(pattern), null); // null = case sensitive
  }
}
