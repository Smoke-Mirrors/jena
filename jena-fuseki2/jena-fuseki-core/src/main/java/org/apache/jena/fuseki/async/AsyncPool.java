/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.fuseki.async;

import static java.lang.String.format ;

import java.util.* ;
import java.util.concurrent.* ;

import org.apache.jena.fuseki.Fuseki ;
import org.apache.jena.fuseki.server.DataService ;

/** The set of currently active async tasks */
public class AsyncPool
{
    private static int nMaxThreads = 4 ;
    private static int MAX_FINISHED = 20 ;
    
    // See Executors.newCachedThreadPool and Executors.newFixedThreadPool 
    private ExecutorService executor = new ThreadPoolExecutor(0, nMaxThreads,
                                                              120L, TimeUnit.SECONDS,
                                                              new LinkedBlockingQueue<Runnable>()) ;
    private final Object mutex = new Object() ; 
    private long counter = 0 ;
    private Map<String, AsyncTask> runningTasks = new LinkedHashMap<>() ; 
    private Map<String, AsyncTask> finishedTasks = new LinkedHashMap<>() ;

    // forms a ring buffer so we can remove in reverse order
    private List<AsyncTask> finishedTasksInOrder = new ArrayList<>();
    
    private static AsyncPool instance = new AsyncPool() ;
    public static AsyncPool get() 
    { return instance ; }

    private AsyncPool() { }
    
    public AsyncTask submit(Runnable task, String displayName, DataService dataService, long requestId) { 
        synchronized(mutex) {
            String taskId = Long.toString(++counter) ;
            Fuseki.serverLog.info(format("Task : %s : %s",taskId, displayName)) ;
            Callable<Object> c = Executors.callable(task) ;
            AsyncTask asyncTask = new AsyncTask(c, this, taskId, displayName, dataService, requestId) ;
            Future<Object> x = executor.submit(asyncTask) ;
            runningTasks.put(taskId, asyncTask) ;
            return asyncTask ;
        }
    }
    
    public Collection<AsyncTask> tasks() {
        synchronized(mutex) {
            List<AsyncTask> x = new ArrayList<>(runningTasks.size()+finishedTasks.size()) ;
            x.addAll(runningTasks.values()) ;
            x.addAll(finishedTasks.values()) ;
            return x ;
        }
    }
    
    public void finished(AsyncTask task) { 
        synchronized(mutex) {
            String id = task.getTaskId() ;
            runningTasks.remove(id) ;
            while ( finishedTasks.size() >= MAX_FINISHED ) {
                AsyncTask removeTask = finishedTasksInOrder.get(0) ;
                finishedTasksInOrder.remove(0) ;
                finishedTasks.remove(removeTask.getTaskId()) ;
            }
            finishedTasks.put(id, task) ;
            finishedTasksInOrder.add(task);
        }
    }

    public AsyncTask getRunningTask(String taskId) {
        synchronized(mutex) {
            return runningTasks.get(taskId) ;
        }
    }

    /** Get for any state */
    public AsyncTask getTask(String taskId) {
        synchronized(mutex) {
            AsyncTask task = runningTasks.get(taskId) ;
            if ( task != null )
                return task ;
            return finishedTasks.get(taskId) ;
        }
    }
}

