delete from game_requirement where game_slug like 'test-%';
delete from game where slug like 'test-%';
delete from cpu_tier where key like 'testcpu%';
delete from gpu_tier where key like 'testgpu%';
