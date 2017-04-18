
            $(document).ready(function() {
              var owl = $('.owl-carousel');
              owl.owlCarousel({
                margin: 30,
                loop:true,
   				autoplay:true,
    			autoplayTimeout:3000,
				navigation: false,
				nav: true,
				dots: false,
   				autoplayHoverPause:true,
                responsive: {
                  0: {
                    items: 1
                  },
                  600: {
                    items: 3
                  },
                  1000: {
                    items: 4
                  }
                }
              })
			  
			  
			  
            })
        

