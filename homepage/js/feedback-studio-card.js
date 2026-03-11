(function () {
  const preview = document.getElementById('fscPreview');
  const link    = document.getElementById('fscLink');
  if (!preview || !link) return;

  const PARTICLES = [
    { size: 3, left: '15%', color: '#7ad7ff', dur: '6s',   delay: '0s'    },
    { size: 2, left: '30%', color: '#9bffcf', dur: '8s',   delay: '1.2s'  },
    { size: 4, left: '50%', color: '#b57aff', dur: '7s',   delay: '0.4s'  },
    { size: 2, left: '68%', color: '#7ad7ff', dur: '9s',   delay: '2s'    },
    { size: 3, left: '82%', color: '#9bffcf', dur: '6.5s', delay: '0.8s'  },
    { size: 2, left: '22%', color: '#ffdd88', dur: '7.5s', delay: '3s'    },
    { size: 3, left: '44%', color: '#7ad7ff', dur: '5.5s', delay: '1.6s'  },
    { size: 2, left: '72%', color: '#b57aff', dur: '8.5s', delay: '0.2s'  },
    { size: 4, left: '88%', color: '#9bffcf', dur: '6.8s', delay: '2.4s'  },
    { size: 2, left: '58%', color: '#ffdd88', dur: '7.2s', delay: '1s'    },
  ];

  const particlesDiv = document.createElement('div');
  particlesDiv.className = 'fs-particles';
  PARTICLES.forEach(function (p) {
    const el = document.createElement('div');
    el.className = 'fs-p';
    el.style.cssText =
      'width:' + p.size + 'px;height:' + p.size + 'px;' +
      'left:' + p.left + ';background:' + p.color + ';' +
      'animation-duration:' + p.dur + ';animation-delay:' + p.delay + ';';
    particlesDiv.appendChild(el);
  });
  preview.insertBefore(particlesDiv, preview.firstChild);

  // Each entry is an array of tokens: plain string OR [type, text]
  var FRAGS = [
    [['kw','lambda'],' x: x',['op','**'],['num','2']],
    [['kw','@'],['fn','lru_cache'],'(maxsize=',['kw','None'],')'],
    [['kw','yield'],' ',['kw','from'],' ',['fn','chain'],'(a, b)'],
    [['kw','async'],' ',['kw','def'],' ',['fn','fetch'],'(url: ',['fn','str'],'):'],
    [['kw','raise'],' ',['fn','ValueError'],'(',['str','"unexpected"'],')'],
    [['kw','with'],' ',['fn','open'],'(',['str','"data.csv"'],') ',['kw','as'],' f:'],
    ['*a, b ',['op','='],' xs'],
    [['kw','class'],' ',['fn','Stack'],['op','['],['fn','T'],['op',']'],':'],
    ['  ',['fn','__slots__'],' ',['op','='],' ',['str',"('_data',)"]],
    [['fn','sorted'],'(d, key',['op','='],'d.get, reverse',['op','='],['kw','True'],')'],
    [['kw','if'],' x ',['kw','is'],' ',['kw','not'],' ',['kw','None'],':'],
    [['kw','match'],' event',['op','.'],'type:'],
    ['  ',['kw','case'],' ',['str','"click"'],' | ',['str','"tap"'],':'],
    [['fn','Counter'],'(words)',['op','.'],['fn','most_common'],'(',['num','5'],')'],
    [['num','0xFF'],' ',['op','&'],' ',['num','0b11001100']],
    ['(',['kw','x'],' ',['op','**'],' ',['num','2'],' ',['kw','for'],' x ',['kw','in'],' range(',['num','100'],') ',['kw','if'],' x ',['op','%'],' ',['num','3'],')'],
    [['kw','@'],['fn','dataclass'],'(frozen',['op','='],['kw','True'],')'],
    [['cm','# type: ignore[assignment]']],
    [['kw','def'],' ',['fn','add'],'(a: ',['fn','int'],', b: ',['fn','int'],') -> ',['fn','int'],':'],
    ['  ',['kw','return'],' a ',['op','+'],' b'],
    [['fn','map'],'(',['kw','lambda'],' s: s',['op','.'],['fn','strip'],'(), lines)'],
    [['fn','zip'],'(keys, ',['fn','map'],'(',['fn','int'],', vals))'],
    [['kw','from'],' ',['fn','functools'],' ',['kw','import'],' ',['fn','reduce']],
    [['fn','reduce'],'(',['kw','lambda'],' a,b: a',['op','+'],'b, xs, ',['num','0'],')'],
    [['kw','global'],' _cache'],
    [['fn','__all__'],' ',['op','='],' [',['str','"parse"'],', ',['str','"render"'],']'],
    [['kw','if'],' ',['fn','__name__'],' ',['op','=='],' ',['str','"__main__"'],':'],
    [['cm','# O(n\u00b7log n) \u2014 Timsort']],
    [['kw','def'],' ',['fn','memoize'],'(fn):'],
    ['  cache: ',['fn','dict'],' ',['op','='],' {}'],
    ['  ',['kw','def'],' ',['fn','wrapper'],'(',['op','*'],'args):'],
    ['    ',['kw','return'],' cache.',['fn','setdefault'],'(args, ',['fn','fn'],'(',['op','*'],'args))'],
    [['kw','async'],' ',['kw','with'],' ',['fn','asyncio'],['op','.'],['fn','timeout'],'(',['num','5'],'):'],
    [['fn','heapq'],['op','.'],['fn','nlargest'],'(',['num','3'],', xs, key',['op','='],['fn','abs'],')'],
    ['xs[::',['op','-'],['num','1'],']\u00a0\u00a0',['cm','# reverse']],
    [['fn','dict'],['op','.'],['fn','fromkeys'],'(lst, ',['num','0'],')'],
    [['fn','set'],'(a) ',['op','^'],' ',['fn','set'],'(b)\u00a0\u00a0',['cm','# symmetric diff']],
    [['fn','isinstance'],'(x, (',['fn','int'],', ',['fn','float'],')),'],
    [['kw','try'],':'],
    ['  x ',['op','='],' ',['fn','json'],['op','.'],['fn','loads'],'(raw)'],
    [['kw','except'],' (',['fn','ValueError'],', ',['fn','TypeError'],') ',['kw','as'],' e:'],
    ['  ',['fn','log'],['op','.'],['fn','warning'],'(',['str','"parse error"'],', exc_info',['op','='],['kw','True'],')'],
    [['kw','def'],' ',['fn','chunks'],'(xs, n',['op',':'],['fn','int'],'):'],
    ['  ',['kw','yield from'],' (xs[i:i',['op','+'],'n] ',['kw','for'],' i ',['kw','in'],' range(',['num','0'],',',['fn','len'],'(xs),n))'],
    [['cm','# walrus operator']],
    [['kw','while'],' chunk ',['op',':='],' f',['op','.'],['fn','read'],'(',['num','4096'],'): buf',['op','+='],'chunk'],
    [['fn','pathlib'],['op','.'],['fn','Path'],'(',['str','"src"'],')',['op','.'],['fn','rglob'],'(',['str','"*.py"'],')'],
    ['T ',['op','='],' ',['fn','TypeVar'],'(',['str','"T"'],', bound',['op','='],['fn','Comparable'],')'],
    [['kw','def'],' ',['fn','clamp'],'(v,lo,hi): ',['kw','return'],' ',['fn','max'],'(lo,',['fn','min'],'(v,hi))'],
    [['num','1_000_000'],' - ',['fn','len'],'(data) ',['op','<<'],' ',['num','2']],
  ];

  var DRIFTS = ['fsc-drift-a','fsc-drift-b','fsc-drift-c','fsc-drift-d','fsc-drift-e','fsc-drift-f'];

  function buildFrag(tokens) {
    var el = document.createElement('div');
    el.className = 'fsc-frag';
    tokens.forEach(function (tok) {
      if (typeof tok === 'string') {
        el.appendChild(document.createTextNode(tok));
      } else {
        var s = document.createElement('span');
        s.className = 'fsc-' + tok[0];
        s.textContent = tok[1];
        el.appendChild(s);
      }
    });
    var left  = (Math.random() * 88).toFixed(1);
    var top   = (Math.random() * 85).toFixed(1);
    var rot   = ((Math.random() - 0.5) * 54).toFixed(1);
    var sz    = (0.54 + Math.random() * 0.22).toFixed(2);
    var dur   = (9 + Math.random() * 13).toFixed(1);
    var delay = (Math.random() * -12).toFixed(1);
    var drift = DRIFTS[Math.floor(Math.random() * DRIFTS.length)];
    el.style.left            = left + '%';
    el.style.top             = top  + '%';
    el.style.transform       = 'rotate(' + rot + 'deg)';
    el.style.fontSize        = sz + 'rem';
    el.style.animationName          = drift;
    el.style.animationDuration      = dur   + 's';
    el.style.animationDelay         = delay + 's';
    return el;
  }

  FRAGS.forEach(function (tokens) {
    preview.insertBefore(buildFrag(tokens), preview.firstChild);
  });

  link.addEventListener('mousemove', function (e) {
    const rect = preview.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width)  * 100;
    const y = ((e.clientY - rect.top)  / rect.height) * 100;
    preview.style.setProperty('--mx', x + '%');
    preview.style.setProperty('--my', y + '%');
    const tx = (x / 100 - 0.5) * 20;
    const ty = (y / 100 - 0.5) * -20;
    preview.style.transform =
      'perspective(600px) rotateX(' + ty + 'deg) rotateY(' + tx + 'deg) scale(1.03)';
  });

  link.addEventListener('mouseleave', function () {
    preview.style.transform = 'perspective(600px) rotateX(0deg) rotateY(0deg) scale(1)';
    preview.style.setProperty('--mx', '50%');
    preview.style.setProperty('--my', '50%');
  });
})();
