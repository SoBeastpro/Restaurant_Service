import assert from 'node:assert/strict';
import { registerHooks } from 'node:module';
registerHooks({resolve(s,c,next){return next(s.startsWith('.')&&!/\.[cm]?js$/.test(s)?`${s}.js`:s,c)}});
process.env.EXPO_PUBLIC_API_URL='http://test.invalid';
const remote=await import('../src/api/remote.js'),http=await import('../src/api/http.js');
const { activeShiftMemberIds } = await import('../src/domain/participants.js');
assert.deepEqual(activeShiftMemberIds([
  {id:'director',membershipId:'m1',role:'MANAGER',backendRole:'DIRECTOR',status:'ACTIVE'},
  {id:'manager',membershipId:'m2',role:'MANAGER',backendRole:'MANAGER',status:'ACTIVE'},
  {id:'employee',membershipId:'m3',role:'WAITER',backendRole:'EMPLOYEE',status:'ACTIVE'},
  {id:'invited',membershipId:'m4',role:'RUNNER',backendRole:'EMPLOYEE',status:'INVITED'},
], ['director','manager','employee','invited','employee']), ['employee']);
const originalFetch=globalThis.fetch, requests=[];
const json=(body,status=200)=>new Response(JSON.stringify(body),{status});
let staff=false;
const current={id:'s',status:'ACTIVE',participants:[{membershipId:'m2',position:'WAITER',tableIds:['t']}],assignedTableIds:['t'],tasks:[{id:'task',assignedToMembershipId:'m2',status:'CREATED'}]};
try{
 globalThis.fetch=async(url,options)=>{
  const p=new URL(url).pathname;requests.push({path:p,method:options.method,body:options.body&&JSON.parse(options.body)});
  if(p==='/api/v1/me')return json({id:staff?'u2':'u1',restaurantId:'r',role:staff?'EMPLOYEE':'DIRECTOR',position:staff?'WAITER':null});
  if(p.endsWith('/restaurants/r'))return json({name:'Ресторан'});
  if(p.endsWith('/halls'))return json([{id:'h',name:'Зал',layout:{canvasWidth:1200,canvasHeight:800,items:[{type:'BAR',positionX:500,positionY:400,width:120,height:40}]}}]);
  if(p.endsWith('/tables'))return json([{id:'t',hallId:'h',number:'1',seatsCount:7,positionX:40,positionY:40,shape:'ROUND',rotationDegrees:90,enabled:true,status:'READY'}]);
  if(p.endsWith('/team')){assert.equal(staff,false);return json([{membershipId:'m1',userId:'u1',role:'DIRECTOR'},{membershipId:'m2',userId:'u2',role:'EMPLOYEE',position:'Официант',status:'ACTIVE'},{membershipId:'m3',role:'EMPLOYEE',status:'INVITED'}])}
  if(p.endsWith('/invitations'))return json([{id:'invite',employeeId:'m3',status:'PENDING',role:'EMPLOYEE',position:'RUNNER',invitationUrl:'rest://invite/secret-token',expiresAt:'2099-01-01T00:00:00Z'}]);
  if(p.endsWith('/me/current-shift')||p.endsWith('/participants'))return json(current);
  if(p.endsWith('/shifts'))return json([current]);
  if(p.endsWith('/shifts/s')){assert.equal(staff,false);return json(current)}
  if(p.endsWith('/notifications'))return json([]);
  throw Error('Unexpected request '+p);
 };
 http.setTokens({accessToken:'token',refreshToken:'refresh',expiresIn:600});
 const manager=await remote.hydrateRemote(http.getTokens());
 assert.equal(manager.snapshot.halls[0].layout.objects[0].type,'BAR');
 assert.equal(manager.snapshot.tables[0].rotation,90);
 assert.equal(manager.snapshot.users.find(u=>u.id==='u2').role,'WAITER');
 assert.equal(manager.snapshot.users.find(u=>u.id==='m3').invitationToken,'secret-token');
 requests.length=0;await remote.configureShiftRemote('r','s',['m2'],{m2:[]});assert.equal(requests.length,1);assert.equal(requests[0].method,'PUT');assert.deepEqual(requests[0].body.participants,[{membershipId:'m2',tableIds:[]}]);
 staff=true;requests.length=0;const employee=await remote.hydrateRemote(http.getTokens());assert.equal(employee.snapshot.tables[0].assigneeId,'u2');assert.equal(employee.snapshot.tasks[0].assigneeId,'u2');assert.ok(!requests.some(r=>r.path.endsWith('/team')||r.path.endsWith('/shifts/s')));
  current.assignedTableIds=[];current.participants[0].tableIds=[];
  const withoutTables=await remote.hydrateRemote(http.getTokens());
  assert.equal(withoutTables.snapshot.tasks[0].assigneeId,'u2','A current employee must keep personal tasks even without tables');
 let posts=0, failPut=true;
 const draft={width:1200,height:800,objects:[],tables:[{id:'draft',isNew:true,number:'2',seats:4,positionX:100,positionY:100,shape:'SQUARE',rotation:0,active:true}]};
 globalThis.fetch=async(url,options)=>{if(options.method==='POST'){posts++;return json({id:'created'})}const b=JSON.parse(options.body);assert.equal(b.layout.canvasWidth,1200);assert.equal(b.tables[0].id,'created');assert.equal(b.tables[0].enabled,true);return failPut?json({detail:'Сбой'},500):json({hall:{},tables:[]})};
 await assert.rejects(()=>remote.saveHallLayoutRemote('r','h',draft,(old,id)=>{draft.tables[0]={...draft.tables[0],id,isNew:false}}),/Создано столов: 1/);
 failPut=false;await remote.saveHallLayoutRemote('r','h',draft);assert.equal(posts,1);
 let refreshes=0;http.setTokens({accessToken:'expired',refreshToken:'old'});
 globalThis.fetch=async(url,options)=>{if(url.endsWith('/auth/refresh')){refreshes++;await new Promise(r=>setTimeout(r,5));return json({accessToken:'new',refreshToken:'new-refresh'})}return options.headers.Authorization==='Bearer new'?json({ok:true}):json({},401)};
 await Promise.all([http.request('/one'),http.request('/two')]);assert.equal(refreshes,1);
  globalThis.fetch=async()=>json({id:'restaurant',tokens:{accessToken:'with-restaurant',refreshToken:'restaurant-refresh',expiresIn:600}});
  await remote.createRestaurantRemote({name:'Ресторан',address:'Адрес',hours:'10:00-22:00'});
  assert.equal(http.getTokens().accessToken,'with-restaurant');
  let closeBody;
  globalThis.fetch=async(url,options)=>{if(options.method==='POST'){closeBody=JSON.parse(options.body);return json({shiftId:'s'})}return json({totalTasks:10,completedTasks:7,carriedOverTasks:2,cancelledTasks:1})};
  const report=await remote.closeShiftRemote('r','s',[{taskId:'task',action:'CARRY_OVER'}]);
  assert.equal(closeBody.unfinishedTasks[0].action,'CARRY_OVER');
  assert.deepEqual(report.report,{created:10,done:7,carried:2,cancelled:1});
 console.log('API adapters passed: backend-dev contract, staff access, shift PUT, partial-save retry, single JWT refresh.');
}finally{globalThis.fetch=originalFetch}
